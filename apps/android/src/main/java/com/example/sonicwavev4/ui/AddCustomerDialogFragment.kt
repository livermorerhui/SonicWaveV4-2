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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.sonicwavev4.core.account.CustomerEvent
import com.example.sonicwavev4.ui.customer.CustomerViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

class AddCustomerDialogFragment : DialogFragment() {

    private var _binding: DialogAddCustomerBinding? = null
    private val binding get() = _binding!!

    private val customerViewModel: CustomerViewModel by activityViewModels()

    private var customerToEdit: Customer? = null

    private lateinit var genderOptions: Array<String>

    private fun formatDoubleForDisplay(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            value.toString()
        }
    }

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
            if (!customer.dateOfBirth.isNullOrEmpty()) {
                val dateOnly = customer.dateOfBirth.take(10) // Take YYYY-MM-DD part
                val dateParts = dateOnly.split("-")
                if (dateParts.size == 3) {
                    binding.yearEditText.setText(dateParts[0])
                    binding.monthEditText.setText(dateParts[1])
                    binding.dayEditText.setText(dateParts[2])
                }
            }
            binding.genderEditText.setText(customer.gender, false)
            binding.phoneEditText.setText(customer.phone)
            binding.emailEditText.setText(customer.email)
            binding.heightEditText.setText(formatDoubleForDisplay(customer.height))
            binding.weightEditText.setText(formatDoubleForDisplay(customer.weight))
            binding.saveButton.text = "更新"
        }

        setupClickListeners()
        observeEvents()
    }

    private fun setupClickListeners() {
        binding.closeButton.setOnClickListener {
            dismiss()
        }

        binding.genderEditText.setOnClickListener {
            binding.genderEditText.showDropDown()
        }

        // --- Instant Validation Logic ---
        binding.yearEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validateYear()
        }

        binding.monthEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validateMonth()
        }

        binding.dayEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validateDay()
        }

        // --- Final Validation and Save Logic ---
        binding.saveButton.setOnClickListener {
            val name = binding.nameEditText.text.toString()
            val email = binding.emailEditText.text?.toString() ?: ""
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

            // Run all validations before saving
            val isYearValid = validateYear()
            val isMonthValid = validateMonth()
            val isDayValid = validateDay()
            if (!isYearValid || !isMonthValid || !isDayValid) {
                Toast.makeText(requireContext(), "请修正日期中的错误", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val yearStr = binding.yearEditText.text?.toString() ?: ""
            val monthStr = binding.monthEditText.text?.toString() ?: ""
            val dayStr = binding.dayEditText.text?.toString() ?: ""
            var dateOfBirth = ""

            if (yearStr.isNotBlank() || monthStr.isNotBlank() || dayStr.isNotBlank()) {
                val year = yearStr.toInt()
                val month = monthStr.toInt()
                val day = dayStr.toInt()

                val userDate = Calendar.getInstance().apply { set(year, month - 1, day) }
                if (userDate.after(Calendar.getInstance())) {
                    Toast.makeText(requireContext(), "出生日期不能是未来的日期", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                dateOfBirth = String.format("%d-%02d-%02d", year, month, day)
            }

            val finalHeight = binding.heightEditText.text?.toString()?.toDoubleOrNull() ?: 0.0
            val finalWeight = binding.weightEditText.text?.toString()?.toDoubleOrNull() ?: 0.0

            val customer = Customer(
                id = customerToEdit?.id,
                name = name,
                email = email,
                dateOfBirth = dateOfBirth,
                gender = gender,
                phone = binding.phoneEditText.text?.toString() ?: "",
                height = finalHeight,
                weight = finalWeight
            )

            if (customerToEdit != null && customerToEdit?.id != null) {
                customerViewModel.updateCustomer(customerToEdit!!.id!!, customer)
            } else {
                customerViewModel.addCustomer(customer)
            }
        }
    }

    private fun validateYear(): Boolean {
        val yearStr = binding.yearEditText.text?.toString() ?: ""
        if (yearStr.isBlank() && binding.monthEditText.text?.isBlank() == true && binding.dayEditText.text?.isBlank() == true) return true // Allow empty date
        val year = yearStr.toIntOrNull()
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        return if (year == null || year < 1900 || year > currentYear) {
            binding.yearLayout.error = "年份无效"
            false
        } else {
            binding.yearLayout.error = null
            true
        }
    }

    private fun validateMonth(): Boolean {
        val monthStr = binding.monthEditText.text?.toString() ?: ""
        if (monthStr.isBlank() && binding.yearEditText.text!!.isBlank() && binding.dayEditText.text!!.isBlank()) return true // Allow empty date
        val month = monthStr.toIntOrNull()
        return if (month == null || month !in 1..12) {
            binding.monthLayout.error = "月份必须是1-12"
            false
        } else {
            binding.monthLayout.error = null
            true
        }
    }

    private fun validateDay(): Boolean {
        val dayStr = binding.dayEditText.text?.toString() ?: ""
        if (dayStr.isBlank() && binding.yearEditText.text!!.isBlank() && binding.monthEditText.text!!.isBlank()) return true // Allow empty date

        val day = dayStr.toIntOrNull()
        val year = binding.yearEditText.text?.toString()?.toIntOrNull()
        val month = binding.monthEditText.text?.toString()?.toIntOrNull()

        if (day == null) {
            binding.dayLayout.error = "日期无效"
            return false
        }
        if (year == null || month == null || month !in 1..12) {
             // Error already handled by other validators, just fail here
            return false
        }

        val calendar = Calendar.getInstance().apply { set(year, month - 1, 1) }
        val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        return if (day !in 1..maxDay) {
            binding.dayLayout.error = "日期对于${month}月无效"
            false
        } else {
            binding.dayLayout.error = null
            true
        }
    }





    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                customerViewModel.events.collectLatest { event ->
                    when (event) {
                        is CustomerEvent.CustomerSaved -> {
                            Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                            dismiss()
                        }
                        is CustomerEvent.Error -> {
                            Toast.makeText(requireContext(), event.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        val dialogWidth = (width * 0.5).toInt()
        val dialogHeight = (height * 0.8).toInt()

        dialog?.window?.setLayout(dialogWidth, dialogHeight)
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