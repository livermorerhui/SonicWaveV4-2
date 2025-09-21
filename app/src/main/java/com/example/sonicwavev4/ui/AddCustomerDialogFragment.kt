package com.example.sonicwavev4.ui

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.os.Bundle
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.sonicwavev4.R
import com.example.sonicwavev4.databinding.DialogAddCustomerBinding
import com.example.sonicwavev4.network.Customer
import com.example.sonicwavev4.ui.user.UserViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

class AddCustomerDialogFragment : DialogFragment() {

    private var _binding: DialogAddCustomerBinding? = null
    private val binding get() = _binding!!

    private val userViewModel: UserViewModel by activityViewModels()

    private var customerToEdit: Customer? = null

    private lateinit var genderOptions: Array<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { bundle ->
            customerToEdit = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // 适用于 Android 13 (API 33) 及以上版本
                bundle.getParcelable(ARG_CUSTOMER, Customer::class.java)
            } else {
                // 适用于旧版本
                @Suppress("DEPRECATION")
                bundle.getParcelable(ARG_CUSTOMER)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddCustomerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        genderOptions = resources.getStringArray(R.array.gender_options)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, genderOptions)
        binding.genderEditText.setAdapter(adapter)

        customerToEdit?.let { customer ->
            binding.nameEditText.setText(customer.name)
            binding.dobEditText.setText(customer.dateOfBirth)
            binding.genderEditText.setText(customer.gender, false) // Set text and prevent dropdown from showing immediately
            binding.phoneEditText.setText(customer.phone)
            binding.emailEditText.setText(customer.email)
            binding.heightEditText.setText(customer.height.toString())
            binding.weightEditText.setText(customer.weight.toString())
            binding.saveButton.text = "更新"
        }

        setupClickListeners()
        setupValidation()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.closeButton.setOnClickListener {
            dismiss()
        }

        binding.dobEditText.setOnClickListener {
            showDatePickerDialog()
        }

        binding.genderEditText.setOnClickListener {
            binding.genderEditText.showDropDown()
        }

        binding.saveButton.setOnClickListener {
            val name = binding.nameEditText.text.toString()
            val email = binding.emailEditText.text.toString()
            val gender = binding.genderEditText.text.toString()

            if (name.isBlank() || email.isBlank()) {
                Toast.makeText(requireContext(), "客户姓名和邮箱不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.emailLayout.error = "Email格式不正确"
                binding.emailEditText.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.shake))
                return@setOnClickListener
            }

            if (!genderOptions.contains(gender)) {
                Toast.makeText(requireContext(), "请选择有效的性别", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val customer = Customer(
                id = customerToEdit?.id, // Pass ID if in update mode
                name = name,
                email = email,
                dateOfBirth = binding.dobEditText.text.toString(),
                gender = gender,
                phone = binding.phoneEditText.text.toString(),
                height = binding.heightEditText.text.toString().toDoubleOrNull() ?: 0.0,
                weight = binding.weightEditText.text.toString().toDoubleOrNull() ?: 0.0
            )

            if (customerToEdit != null && customerToEdit?.id != null) {
                userViewModel.updateCustomer(customerToEdit!!.id!!, customer)
            } else {
                userViewModel.addCustomer(customer)
            }
        }
    }

    private fun setupValidation() {
        binding.emailEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val email = s.toString()
                if (email.isNotBlank() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    binding.emailLayout.error = "Email格式不正确"
                } else {
                    binding.emailLayout.error = null
                }
            }
        })
    }

    @SuppressLint("DefaultLocale")
    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
            val formattedDate = String.format("%d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay)
            binding.dobEditText.setText(formattedDate)
        }, year, month, day).show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            userViewModel.addCustomerResult.collectLatest { result ->
                result?.let {
                    if (it.isSuccess) {
                        Toast.makeText(requireContext(), "客户添加成功", Toast.LENGTH_SHORT).show()
                        dismiss()
                    } else {
                        Toast.makeText(requireContext(), "添加失败: ${it.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                    userViewModel.resetAddCustomerResult() // Reset the state
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            userViewModel.updateCustomerResult.collectLatest { result ->
                result?.let {
                    if (it.isSuccess) {
                        Toast.makeText(requireContext(), "客户更新成功", Toast.LENGTH_SHORT).show()
                        dismiss()
                    } else {
                        Toast.makeText(requireContext(), "更新失败: ${it.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                    userViewModel.resetUpdateCustomerResult() // Reset the state
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_CUSTOMER = "customer"
        fun newInstance(customer: Customer? = null): AddCustomerDialogFragment {
            val fragment = AddCustomerDialogFragment()
            customer?.let {
                val args = Bundle().apply {
                    putParcelable(ARG_CUSTOMER, it)
                }
                fragment.arguments = args
            }
            return fragment
        }
    }
}