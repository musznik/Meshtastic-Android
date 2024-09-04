package com.geeksville.mesh.ui

import WebBrowserViewModel
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.LinearLayout
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

    private var indexRequestStartTime: Long = 0L
    private var indexRetryCount: Int = 0
    private val maxRetryDuration: Long = 60_000 //1min
    private val retryInterval: Long = 8_000
    private lateinit var segmentIndicatorContainer: LinearLayout


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

        segmentIndicatorContainer = binding.segmentIndicatorContainer

        setupWebView()
        requestIndexMd(contactKey)
    }

    private fun createSegmentIndicators(totalSegments: Int) {
        segmentIndicatorContainer.removeAllViews()

        for (i in 1..totalSegments) {
            val indicator = ImageView(requireContext())
            val size = 25

            val scale = resources.displayMetrics.density
            val params = LinearLayout.LayoutParams((size * scale).toInt(), (size * scale).toInt())
            params.setMargins(4, 0, 4, 0)

            indicator.layoutParams = params

            val drawable = GradientDrawable()
            drawable.shape = GradientDrawable.RECTANGLE
            drawable.setStroke(2, Color.BLACK)
            drawable.setColor(Color.WHITE)

            indicator.setImageDrawable(drawable)

            segmentIndicatorContainer.addView(indicator)
        }
    }

    private fun clearSegmentIndicator() {
        segmentIndicatorContainer.removeAllViews()
    }

    private fun updateSegmentIndicator(segmentNumber: Int) {
        val indicator = segmentIndicatorContainer.getChildAt(segmentNumber - 1) as ImageView
        val drawable = indicator.drawable as GradientDrawable
        drawable.setColor(Color.BLACK)
    }

    private fun onReceivedTotalSegments(totalSegments: Int) {
        createSegmentIndicators(totalSegments)
    }

    private fun onSegmentReceived(segmentNumber: Int) {
        updateSegmentIndicator(segmentNumber)
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
            val segmentToRequest = missingSegments.first()
            binding.progressTextSub.visibility=View.VISIBLE
            binding.progressTextSub.text="ponawianie segmentu $segmentToRequest"
            debug("web: re-requesting segment: $segmentToRequest")
            requestSegment(segmentToRequest)
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
        binding.progressTextSub.text=""
        binding.progressTextSub.visibility=View.GONE

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

    private fun showError(message: String) {
        binding.root.post {
            binding.progressLayout.visibility = View.VISIBLE
            binding.webView.visibility = View.VISIBLE
            binding.progressBar.visibility = View.GONE
            binding.progressText.text = message
            binding.progressTextSub.text=""
            binding.progressTextSub.visibility = View.GONE
            debug("web: $message")
        }
    }

    private fun startIndexRetryTimer() {
        centralRetryJob?.cancel()
        centralRetryJob = coroutineScope.launch {
            var lastRequestTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - indexRequestStartTime < maxRetryDuration) {

                if (receivedSegments.isEmpty() && (System.currentTimeMillis() - lastRequestTime >= retryInterval)) {
                    indexRetryCount++

                        if(indexRetryCount>0){
                            binding.progressTextSub.text = "ponawianie nr " + (indexRetryCount).toString()
                            debug("web: retrying index.md request, attempt: $indexRetryCount")
                        }

                    sendDataRequestForContactWithPayload(arguments?.getString("contactKey").orEmpty(), "r:$currentFileName")

                    lastRequestTime = System.currentTimeMillis()
                } else if (receivedSegments.isNotEmpty()) {
                    binding.progressTextSub.text = ""
                    binding.progressTextSub.visibility = View.GONE
                    break
                }

                delay(retryInterval)
            }

            if (receivedSegments.isEmpty()) {
                showError("Nie można pobrać indeksu, serwer nie odpowiedział")
            }else{
                binding.progressTextSub.text=""
                binding.progressTextSub.visibility = View.GONE
            }
        }
    }

    private fun requestIndexMd(contactKey: String) {
        val requestMessage = "r:$currentFileName"
        currentFileID = generateFileID(currentFileName)
        binding.progressLayout.visibility = View.VISIBLE
        receivedSegments.clear()
        currentSegment = 1
        indexRequestStartTime = System.currentTimeMillis()

        startIndexRetryTimer()
    }

    private fun decompressGzip(compressedData: ByteArray): ByteArray {
        val byteArrayInputStream = ByteArrayInputStream(compressedData)
        val gzipInputStream = GZIPInputStream(byteArrayInputStream)
        return gzipInputStream.readBytes()
    }

    private fun completeLoading() {
        val completeData = if (receivedSegments.isNotEmpty()) {
            receivedSegments.toSortedMap().values.reduce { acc, bytes -> acc + bytes }
        } else {
            ByteArray(0)
        }

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
            centralRetryJob?.cancel()
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
            binding.progressTextSub.text=""
            binding.progressTextSub.visibility = View.GONE
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
                            onReceivedTotalSegments(totalSegments)
                        }

                        onSegmentReceived(segmentNumber)

                        if(receivedSegments.size == 1){
                            binding.progressTextSub.text = ""
                            binding.progressTextSub.visibility = View.GONE
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
                            clearSegmentIndicator()
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
