package com.example.sonicwavev4.ui.customer

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels // 👈 1. 需要引入这个
import com.example.sonicwavev4.databinding.FragmentCustomerDetailBinding
import com.example.sonicwavev4.network.Customer
import com.example.sonicwavev4.ui.user.UserViewModel // 👈 2. 需要引入你的 UserViewModel

class CustomerDetailFragment : Fragment() {

    private var _binding: FragmentCustomerDetailBinding? = null
    private val binding get() = _binding!!

    private var customer: Customer? = null

    // --- 3. 添加这一行 ---
    // 获取对 Activity 范围内的 UserViewModel 的引用
    private val userViewModel: UserViewModel by activityViewModels()

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
            binding.customerNameToolbar.text = it.name
        }

        binding.backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // --- 4. 在这里添加清空逻辑 ---
        // 当这个页面的视图被销毁时（例如用户按返回键），
        // 调用 ViewModel 的方法来清空选中的客户信息。
        userViewModel.clearSelectedCustomer()

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