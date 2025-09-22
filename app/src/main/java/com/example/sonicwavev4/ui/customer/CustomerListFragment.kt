package com.example.sonicwavev4.ui.customer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.sonicwavev4.databinding.FragmentCustomerListBinding
import com.example.sonicwavev4.network.Customer
import com.example.sonicwavev4.ui.AddCustomerDialogFragment
import com.example.sonicwavev4.ui.user.UserViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CustomerListFragment : Fragment() {

    private var _binding: FragmentCustomerListBinding? = null
    private val binding get() = _binding!!

    private val userViewModel: UserViewModel by activityViewModels()
    private lateinit var customerAdapter: CustomerAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCustomerListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModel()

        // Fetch customers when the fragment is created
        userViewModel.fetchCustomers()
    }

    private fun setupRecyclerView() {
        customerAdapter = CustomerAdapter(
            onEditClick = { customer ->
                // Handle edit click: open dialog in update mode
                AddCustomerDialogFragment.newInstance(customer).show(parentFragmentManager, "AddCustomerDialog")
            },
            onItemSelected = { customer ->
                // Handle item selection: e.g., log it or update a ViewModel
                // For now, we'll just log it.
                println("Selected customer: ${customer.name}")
            }
        )
        binding.customerRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = customerAdapter
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            userViewModel.customers.collectLatest {
                customers ->
                binding.loadingSpinner.visibility = View.GONE
                if (customers.isNullOrEmpty()) {
                    binding.emptyView.visibility = View.VISIBLE
                    binding.customerRecyclerView.visibility = View.GONE
                } else {
                    binding.emptyView.visibility = View.GONE
                    binding.customerRecyclerView.visibility = View.VISIBLE
                    customerAdapter.submitList(customers)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            userViewModel.loading.collectLatest {
                isLoading ->
                binding.loadingSpinner.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
