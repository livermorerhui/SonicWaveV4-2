package com.example.sonicwavev4.ui.customer

import com.example.sonicwavev4.R
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.sonicwavev4.core.AppMode
import com.example.sonicwavev4.core.CustomerSource
import com.example.sonicwavev4.core.account.CustomerEvent
import com.example.sonicwavev4.core.currentAppMode
import com.example.sonicwavev4.databinding.FragmentCustomerListBinding
import com.example.sonicwavev4.ui.AddCustomerDialogFragment
import com.example.sonicwavev4.ui.OfflineAddCustomerDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CustomerListFragment : Fragment() {

    private var _binding: FragmentCustomerListBinding? = null
    private val binding get() = _binding!!

    private val customerViewModel: CustomerViewModel by activityViewModels()
    private lateinit var customerAdapter: CustomerAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCustomerListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearchBox()
        observeViewModel()
        observeEvents()
        setupAddCustomerButton()

        customerViewModel.loadCustomers()
    }

    private fun setupRecyclerView() {
        customerAdapter = CustomerAdapter(
            onEditClick = { customer ->
                AddCustomerDialogFragment.newInstance(customer).show(parentFragmentManager, "AddCustomerDialog")
            },
            onItemSelected = { customer ->
                customerViewModel.selectCustomer(customer)
            },
            onItemDoubleClick = { customer ->
                customerViewModel.selectCustomer(customer)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.customer_list_container, CustomerDetailFragment.newInstance(customer))
                    .addToBackStack(null)
                    .commit()
            }
        )
        binding.customerRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = customerAdapter
        }
    }

    private fun setupAddCustomerButton() {
        binding.addCustomerButton.setOnClickListener {
            if (currentAppMode() == AppMode.OFFLINE) {
                OfflineAddCustomerDialogFragment.newInstance().show(parentFragmentManager, "OfflineAddCustomerDialog")
            } else {
                AddCustomerDialogFragment.newInstance().show(parentFragmentManager, "AddCustomerDialog")
            }
        }
    }

    private fun setupSearchBox() {
        binding.searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                customerViewModel.setSearchQuery(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                customerViewModel.uiState.collectLatest { state ->
                    binding.loadingSpinner.isVisible = state.isLoading
                    val customers = state.filteredCustomers
                    if (customers.isEmpty()) {
                        binding.emptyView.visibility = View.VISIBLE
                        binding.customerRecyclerView.visibility = View.GONE
                        binding.emptyView.text = if (state.source == CustomerSource.OFFLINE) {
                            getString(R.string.offline_mode_hint)
                        } else {
                            getString(R.string.no_data)
                        }
                    } else {
                        binding.emptyView.visibility = View.GONE
                        binding.customerRecyclerView.visibility = View.VISIBLE
                        customerAdapter.submitList(customers)
                    }
                }
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                customerViewModel.events.collectLatest { event ->
                    when (event) {
                        is CustomerEvent.CustomerSaved -> Unit
                        is CustomerEvent.Error -> Toast.makeText(requireContext(), event.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
