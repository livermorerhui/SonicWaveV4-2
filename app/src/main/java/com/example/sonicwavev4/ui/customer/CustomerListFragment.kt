package com.example.sonicwavev4.ui.customer

import com.example.sonicwavev4.R
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.example.sonicwavev4.utils.OfflineTestModeManager
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
        setupSearchBox()
        observeViewModel()

        if (OfflineTestModeManager.isOfflineMode()) {
            binding.emptyView.text = getString(R.string.offline_mode_hint)
        }

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
                println("Selected customer (single click): ${customer.name}")
            },
            onItemDoubleClick = { customer ->
                // Handle double click: select customer and navigate to detail
                userViewModel.selectCustomer(customer)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.customer_list_container, CustomerDetailFragment.newInstance(customer))
                    .addToBackStack(null) // Allow back navigation
                    .commit()
            }
        )
        binding.customerRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = customerAdapter
        }
    }

    private fun setupSearchBox() {
        binding.searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                userViewModel.setSearchQuery(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            userViewModel.filteredCustomers.collectLatest {
                customers ->
                val currentBinding = _binding ?: return@collectLatest
                currentBinding.loadingSpinner.visibility = View.GONE
                if (customers.isNullOrEmpty()) {
                    currentBinding.emptyView.visibility = View.VISIBLE
                    currentBinding.customerRecyclerView.visibility = View.GONE
                } else {
                    currentBinding.emptyView.visibility = View.GONE
                    currentBinding.customerRecyclerView.visibility = View.VISIBLE
                    customerAdapter.submitList(customers)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            userViewModel.loading.collectLatest {
                isLoading ->
                val currentBinding = _binding ?: return@collectLatest
                currentBinding.loadingSpinner.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
