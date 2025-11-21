package com.example.sonicwavev4.ui.customer

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.sonicwavev4.databinding.FragmentCustomerDetailBinding
import com.example.sonicwavev4.network.Customer
import com.example.sonicwavev4.ui.customer.CustomerViewModel

class CustomerDetailFragment : Fragment() {

    private var _binding: FragmentCustomerDetailBinding? = null
    private val binding get() = _binding!!

    private var customer: Customer? = null

    // --- 3. 添加这一行 ---
    private val customerViewModel: CustomerViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            customer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getParcelable(ARG_CUSTOMER, Customer::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getParcelable(ARG_CUSTOMER) as Customer?
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCustomerDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        customer?.let {
            binding.tvCustomerName.text = it.name
            binding.tvCustomerInfo.text = "客户详情"
        }

        binding.backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        customerViewModel.clearSelectedCustomer()

        _binding = null
    }

    companion object {
        private const val ARG_CUSTOMER = "customer"
        fun newInstance(customer: Customer): CustomerDetailFragment {
            val fragment = CustomerDetailFragment()
            val args = Bundle().apply {
                putParcelable(ARG_CUSTOMER, customer)
            }
            fragment.arguments = args
            return fragment
        }
    }
}
