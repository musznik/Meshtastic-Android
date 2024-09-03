package com.geeksville.mesh.ui

import WebBrowserViewModel
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import com.geeksville.mesh.R
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.databinding.WebBrowserFragmentBinding
import com.geeksville.mesh.model.UIViewModel
import dagger.hilt.android.AndroidEntryPoint
import com.geeksville.mesh.*
import com.google.android.material.appbar.MaterialToolbar
import com.geeksville.mesh.repository.datastore.DataPacketEvent
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlinx.coroutines.*
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream


internal fun FragmentManager.navigateToWebBrowser(contactKey: String, contactName: String) {
    val webbrowserFragment = WebBrowserFragment().apply {
        arguments = bundleOf("contactKey" to contactKey, "contactName" to contactName)
    }
    beginTransaction()
        .add(R.id.mainActivityLayout, webbrowserFragment)
        .addToBackStack(null)
        .commit()
}

@AndroidEntryPoint
class WebBrowserFragment : Fragment(), Logging {

    private var _binding: WebBrowserFragmentBinding? = null
    private val binding get() = _binding!!
    private val model: UIViewModel by activityViewModels()

    private var receivedSegments: MutableMap<Int, ByteArray> = mutableMapOf()
    private var totalSegments: Int = 0
    private var currentSegment: Int = 1

    private val viewModel: WebBrowserViewModel by activityViewModels()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val timeoutMap = mutableMapOf<Int, Job>()
    private var retryJob: Job? = null

    private var currentFileName: String = "index.md"
    private var currentFileID: ByteArray = byteArrayOf()
    private var resourceQueue: MutableList<String> = mutableListOf()
    private var isLoadingResources = false

    private var lastSegmentReceivedTime: Long = System.currentTimeMillis()
    private var centralRetryJob: Job? = null


    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
        coroutineScope.cancel()

    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDataPacketEvent(event: DataPacketEvent) {
        processIncomingMessage(event.dataPacket)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = WebBrowserFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as AppCompatActivity).setSupportActionBar(binding.toolbar as MaterialToolbar)
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val contactKey = arguments?.getString("contactKey").orEmpty()
        val contactName = arguments?.getString("contactName").orEmpty()
        binding.toolbar.title = contactName

        setupWebView()
        requestIndexMd(contactKey)
    }

    private fun requestFile(fileName: String, contactKey: String) {
        binding.progressLayout.visibility = View.VISIBLE
        binding.progressBar.visibility = View.VISIBLE
        receivedSegments.clear()
        currentSegment = 1

        currentFileName = fileName
        currentFileID = generateFileID(fileName)

        val requestMessage = "r:$fileName"
        debug("web: requesting file: $fileName")
        sendDataRequestForContactWithPayload(contactKey, requestMessage)
    }

    private fun generateFileID(fileName: String): ByteArray  {
        val md5Bytes = MessageDigest.getInstance("MD5").digest(fileName.toByteArray())
        val result = md5Bytes.copyOfRange(0, 4)
        return result
    }

    private fun setupWebView() {
        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.progressBar.visibility = View.GONE
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url.toString()
                if (url.startsWith("meshtastic://")) {
                    enqueueResourceRequest(url.substringAfter("meshtastic://"))
                    return WebResourceResponse("image/webp", "binary", ByteArrayInputStream(ByteArray(0))) // Placeholder
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String): Boolean {
                handleLinkClick(url)
                return true
            }
        }
    }

    private fun requestResource(resourceName: String) {
        debug("web: requesting resource: $resourceName")
        val requestMessage = "r:$resourceName"
        currentFileID = generateFileID(resourceName)
        currentFileName = resourceName
        sendDataRequestForContactWithPayload(arguments?.getString("contactKey").orEmpty(), requestMessage)
    }

    private fun enqueueResourceRequest(resourceName: String) {
        resourceQueue.add(resourceName)
        if (!isLoadingResources) {
            isLoadingResources = true
            processNextResourceRequest()
        }
    }

    private fun processNextResourceRequest() {
        if (resourceQueue.isNotEmpty()) {
            val nextResource = resourceQueue.removeAt(0)
            resetForNewFileRequest()
            requestResource(nextResource)
        } else {
            isLoadingResources = false
        }
    }

    private fun handleLinkClick(url: String) {
        var fileName = url.substringAfterLast("file://")

        if (fileName.endsWith("/")) {
            fileName = fileName.dropLast(1)
        }

        if (fileName.isNotEmpty()) {
            binding.progressText.text = getString(R.string.reading)+" $fileName ..."
            clearState()
            requestFile(fileName)
        }
    }

    private fun requestFile(fileName: String) {
        resetForNewFileRequest()

        currentFileName = fileName
        currentFileID = generateFileID(fileName)

        val requestMessage = "r:$fileName"
        debug("web: requesting file $fileName")
        sendDataRequestForContactWithPayload(arguments?.getString("contactKey").orEmpty(), requestMessage)
    }


    private fun startCentralTimeout() {
        centralRetryJob?.cancel()
        centralRetryJob = coroutineScope.launch {
            delay(8_000)
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastSegmentReceivedTime >= 8_000) {
                retryMissingSegments()
            }
        }
    }

    private fun retryMissingSegments() {
        val missingSegments = (1..totalSegments).filter { it !in receivedSegments.keys }
        if (missingSegments.isNotEmpty()) {
            debug("web: re-request segment: $missingSegments")
            missingSegments.forEach { requestSegment(it) }
            startCentralTimeout()
        } else {
            isLoadingResources = false
        }
    }

    private fun requestSegment(segmentNumber: Int) {
        val requestMessage = "r:$currentFileName:$segmentNumber"
        sendDataRequestForContactWithPayload(arguments?.getString("contactKey").orEmpty(), requestMessage)
    }

    private fun updateProgressBar() {
        binding.progressBar.max = totalSegments
        binding.progressBar.progress = receivedSegments.size
        binding.progressText.text = "Segment ${receivedSegments.size} "+getString(R.string.of)+" $totalSegments"
    }

    private fun sendDataRequestForContactWithPayload(contactKey: String, data: String){
        val bytes = data.toByteArray(Charsets.UTF_8)


        debug("web: request:  $data")
        val channel = contactKey[0].digitToIntOrNull()
        val dest = if (channel != null) contactKey.substring(1) else contactKey

        val dataPacket = DataPacket(
            to = dest,
            bytes = bytes,
            dataType = 256,
            from = DataPacket.ID_LOCAL,
            time = System.currentTimeMillis(),
            id = 0,
            status = MessageStatus.QUEUED,
            hopLimit = 7,
            channel = 0
        )

        model.sendDataPacket(dataPacket)
    }

    private fun requestIndexMd(contactKey: String) {
        val requestMessage = "r:$currentFileName"
        currentFileID = generateFileID(currentFileName)
        sendDataRequestForContactWithPayload(contactKey,requestMessage)
        binding.progressLayout.visibility = View.VISIBLE
        receivedSegments.clear()
        currentSegment = 1
    }

    private fun decompressGzip(compressedData: ByteArray): ByteArray {
        val byteArrayInputStream = ByteArrayInputStream(compressedData)
        val gzipInputStream = GZIPInputStream(byteArrayInputStream)
        return gzipInputStream.readBytes()
    }

    private fun completeLoading() {
        val completeData = receivedSegments.toSortedMap().values.reduce { acc, bytes -> acc + bytes }
        binding.webView.settings.javaScriptEnabled = true

        debug("web: segment completed, received compressed data size: ${completeData.size} for file ${currentFileName}")

        val decompressedData = decompressGzip(completeData)

        if (currentFileName.endsWith(".webp")) {

            val base64Image = android.util.Base64.encodeToString(decompressedData, android.util.Base64.NO_WRAP)
            
            val javascript = """
            (function() {
                var img = document.querySelector('img[src="meshtastic://$currentFileName"]');
                if (img) {
                    img.src = 'data:image/webp;base64,$base64Image';
                }
            })();
        """.trimIndent()

            binding.webView.post {
                binding.webView.evaluateJavascript(javascript, null)
            }
        } else {
            debug("Setting HTML: $currentFileName")
            val htmlContent = String(decompressedData)
            binding.webView.post {
                binding.webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
            }
        }

        binding.progressLayout.visibility = View.GONE
        binding.webView.visibility = View.VISIBLE
        clearState()
    }

    private fun clearState() {
        receivedSegments.clear()
        totalSegments = 0
        currentSegment = 1
        timeoutMap.clear()
        retryJob?.cancel()
        centralRetryJob?.cancel()
    }

    private fun resetForNewFileRequest() {
        binding.root.post {
            receivedSegments.clear()
            totalSegments = 0
            currentSegment = 1
            timeoutMap.clear()
            retryJob?.cancel()
            binding.progressLayout.visibility = View.VISIBLE
            binding.progressBar.visibility = View.VISIBLE
            binding.progressBar.progress = 0
            binding.progressText.text = getString(R.string.connecting)
            debug("web: reset state for new file request")
        }
    }

    private fun processIncomingMessage(packet: DataPacket) {
        val msg = packet.bytes!!;
        if (msg != null && msg.size > 2 && packet.dataType == Portnums.PortNum.PRIVATE_APP_VALUE)
        {

            val frameIdentifier = msg[0]
            if (frameIdentifier != 0xAB.toByte()) {
                debug("web: wrong packet frame identification, packet ignored: $frameIdentifier")
                return
            }

            val segmentNumber = msg[1].toInt()
            val totalSegmentsReceived = msg[2].toInt()
            val packetID = msg.copyOfRange(3, 7)

            if (packetID.contentEquals(currentFileID)){
                        lastSegmentReceivedTime = System.currentTimeMillis()

                        if (totalSegments == 0) {
                            totalSegments = totalSegmentsReceived
                        }

                        if (segmentNumber in 1..totalSegments) {
                            val data = msg.copyOfRange(7, msg.size)
                            receivedSegments[segmentNumber] = data

                            timeoutMap[segmentNumber]?.cancel()
                            timeoutMap.remove(segmentNumber)

                            updateProgressBar()

                            if (receivedSegments.size == totalSegments) {
                                completeLoading()
                            }

                        } else {
                            debug("web: received wrong segment: $segmentNumber of $totalSegments")
                        }

                        updateProgressBar()

                        if (receivedSegments.size == totalSegments) {
                            completeLoading()
                        } else {
                            startCentralTimeout()
                        }
            } else {
                debug("web: packet ignored. Received ID: ${packetID.joinToString("") { "%02x".format(it) }}, expected: ${currentFileID.joinToString("") { "%02x".format(it) }}")

            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
