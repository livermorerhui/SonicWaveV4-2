package com.example.sonicwavev4.ui.customer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.sonicwavev4.databinding.ItemCustomerBinding
import com.example.sonicwavev4.network.Customer

class CustomerAdapter(private val onEditClick: (Customer) -> Unit) :
    ListAdapter<Customer, CustomerAdapter.CustomerViewHolder>(CustomerDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomerViewHolder {
        val binding = ItemCustomerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CustomerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CustomerViewHolder, position: Int) {
        val customer = getItem(position)
        holder.bind(customer)
    }

    inner class CustomerViewHolder(private val binding: ItemCustomerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.editButton.setOnClickListener { onEditClick(getItem(adapterPosition)) }
        }

        fun bind(customer: Customer) {
            binding.customerNameTextView.text = "客户姓名: ${customer.name}"
            binding.customerEmailTextView.text = "邮箱: ${customer.email}"
            binding.customerPhoneTextView.text = "电话: ${customer.phone}"
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
