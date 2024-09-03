import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.geeksville.mesh.database.entity.Packet

class WebBrowserViewModel : ViewModel() {

    private val _packets = MutableLiveData<List<Packet>>()
    val packets: LiveData<List<Packet>> get() = _packets

    fun updatePackets(newPackets: List<Packet>) {
        _packets.value = newPackets
    }

    fun addPacket(packet: Packet) {
        val updatedList = _packets.value.orEmpty().toMutableList()
        updatedList.add(packet)
        _packets.value = updatedList
    }
}
