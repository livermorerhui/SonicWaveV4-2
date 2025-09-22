package com.example.sonicwavev4.ui.customer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.sonicwavev4.R
import com.example.sonicwavev4.databinding.ItemCustomerBinding
import com.example.sonicwavev4.network.Customer

class CustomerAdapter(
    private val onEditClick: (Customer) -> Unit,
    private val onItemSelected: (Customer) -> Unit,
    private val onItemDoubleClick: (Customer) -> Unit
) : ListAdapter<Customer, CustomerAdapter.CustomerViewHolder>(CustomerDiffCallback()) {

    private var selectedPosition = RecyclerView.NO_POSITION
    private var lastClickTime: Long = 0
    private val DOUBLE_CLICK_TIME_DELTA: Long = 300 // milliseconds

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomerViewHolder {
        val binding = ItemCustomerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CustomerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CustomerViewHolder, position: Int) {
        val customer = getItem(position)
        holder.bind(customer, position == selectedPosition)
    }

    inner class CustomerViewHolder(private val binding: ItemCustomerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.editButton.setOnClickListener { onEditClick(getItem(adapterPosition)) }
            itemView.setOnClickListener {
                val clickTime = System.currentTimeMillis()
                if (clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
                    // Double click detected
                    onItemDoubleClick(getItem(adapterPosition))
                    lastClickTime = 0 // Reset to prevent triple clicks
                } else {
                    // Single click logic (highlighting)
                    val oldSelectedPosition = selectedPosition
                    selectedPosition = adapterPosition

                    // Notify old selected item to un-highlight
                    if (oldSelectedPosition != RecyclerView.NO_POSITION) {
                        notifyItemChanged(oldSelectedPosition)
                    }
                    // Notify new selected item to highlight
                    notifyItemChanged(selectedPosition)

                    onItemSelected(getItem(selectedPosition))
                }
                lastClickTime = clickTime
            }
        }

        fun bind(customer: Customer, isSelected: Boolean) {
            binding.customerNameTextView.text = "${customer.name}"


            // Set background based on selection state
            itemView.isSelected = isSelected
            itemView.setBackgroundResource(R.drawable.item_background_selector)
        }
    }

    class CustomerDiffCallback : DiffUtil.ItemCallback<Customer>() {
        override fun areItemsTheSame(oldItem: Customer, newItem: Customer): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Customer, newItem: Customer): Boolean {
            return oldItem == newItem
        }
    }
}
