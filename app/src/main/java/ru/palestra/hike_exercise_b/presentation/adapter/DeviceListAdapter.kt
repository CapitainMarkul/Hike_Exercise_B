package ru.palestra.hike_exercise_b.presentation.adapter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import ru.palestra.hike_exercise_b.R
import ru.palestra.hike_exercise_b.databinding.ItemDeviceInfoBinding

/** Класс, отвечающий за лдогику отображения элементов списка найденных устройств. */
internal class DeviceListAdapter(
    private val context: Context,
    private val onItemClickListener: (BluetoothDevice) -> Unit
) : RecyclerView.Adapter<DeviceListAdapter.ViewHolder>() {

    private val itemList = mutableSetOf<BluetoothDevice>()

    private val unknownDeviceText by lazy {
        context.getText(R.string.unknown_user_device_info).toString()
    }

    /** Метод для добавления найденного Bluetooth устройства из списка. */
    fun addItem(item: BluetoothDevice) {
        updateListWithDiffUtil(mutableSetOf(*itemList.toTypedArray()).apply { add(item) })
    }

    /** Метод для удаления найденного ранее Bluetooth устройства из списка. */
    fun removeItem(item: BluetoothDevice) {
        updateListWithDiffUtil(mutableSetOf(*itemList.toTypedArray()).apply { removeItem(item) })
    }

    /** Метод для полной очистки списка найденных Bluetooth устройств. */
    @SuppressLint("NotifyDataSetChanged")
    fun removeAll() {
        itemList.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemDeviceInfoBinding.inflate(LayoutInflater.from(context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(itemList.elementAt(position))

    override fun getItemCount(): Int {
        return itemList.size
    }

    private fun updateListWithDiffUtil(newItems: Set<BluetoothDevice>) {
        val diffCallback = BluetoothDeviceDiffUtilCallback(itemList.toList(), newItems.toList())
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        itemList.clear()
        itemList.addAll(newItems)

        diffResult.dispatchUpdatesTo(this)
    }

    internal inner class ViewHolder(private val binding: ItemDeviceInfoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        /** Метод для установки данных в ячейку найденны устройств. */
        @SuppressLint("MissingPermission")
        fun bind(bluetoothDevice: BluetoothDevice) {
            binding.txtDeviceName.text = bluetoothDevice.name ?: unknownDeviceText
            binding.txtDeviceMac.text = bluetoothDevice.address

            binding.root.setOnClickListener { onItemClickListener(bluetoothDevice) }
        }
    }

    private inner class BluetoothDeviceDiffUtilCallback(
        private val oldList: List<BluetoothDevice>,
        private val newList: List<BluetoothDevice>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            oldList[oldItemPosition].address == newList[newItemPosition].address

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            oldList[oldItemPosition] === newList[newItemPosition]
    }
}