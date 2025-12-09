package com.example.sonicwavev4.ui.customer

import android.content.ActivityNotFoundException
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.sonicwavev4.databinding.FragmentCustomerDetailBinding
import com.example.sonicwavev4.network.Customer
import com.example.sonicwavev4.ui.customer.CustomerViewModel
import com.example.sonicwavev4.ui.customer.HumedsLaunchUiState
import kotlinx.coroutines.launch

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

        binding.epcgButton.setOnClickListener {
            customerViewModel.onLaunchHumedsClicked()
        }

        binding.backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        observeHumedsLaunchState()
    }

    private fun launchHumedsAppWithToken(tokenJwt: String? = null) {
        val context = requireContext()
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(HUMEDS_PACKAGE_NAME)

        if (launchIntent == null) {
            Toast.makeText(context, "未安装 Humeds APP", Toast.LENGTH_SHORT).show()
            return
        }

        if (!tokenJwt.isNullOrBlank()) {
            launchIntent.putExtra(HUMEDS_TOKEN_EXTRA, tokenJwt)
        }

        try {
            startActivity(launchIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "未安装 Humeds APP", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeHumedsLaunchState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                customerViewModel.humedsLaunchState.collect { state ->
                    when (state) {
                        is HumedsLaunchUiState.Idle -> binding.epcgButton.isEnabled = true
                        is HumedsLaunchUiState.Loading -> binding.epcgButton.isEnabled = false
                        is HumedsLaunchUiState.Success -> {
                            binding.epcgButton.isEnabled = true
                            launchHumedsAppWithToken(state.tokenJwt)
                            customerViewModel.resetHumedsLaunchState()
                        }
                        is HumedsLaunchUiState.Error -> {
                            binding.epcgButton.isEnabled = true
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                            customerViewModel.resetHumedsLaunchState()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        customerViewModel.clearSelectedCustomer()

        _binding = null
    }

    companion object {
        private const val HUMEDS_PACKAGE_NAME = "com.humeds.epcg"
        const val HUMEDS_TOKEN_EXTRA = "token_jwt"
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
