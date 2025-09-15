package com.example.sonicwavev4.ui.notifications

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.window.layout.WindowMetricsCalculator
import com.example.sonicwavev4.R

class NotificationDialogFragment : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_notifications, container, false)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            // Apply animations
            window.setWindowAnimations(R.style.DialogAnimation)

            // Use WindowMetricsCalculator for accurate screen size
            val windowMetrics = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(requireActivity())
            val bounds = windowMetrics.bounds

            // Set size to 1/2 width and 1/2 height
            val dialogWidth = bounds.width() / 2
            val dialogHeight = bounds.height() / 2
            window.setLayout(dialogWidth, dialogHeight)

            // Position it at the top right
            val params = window.attributes
            params.gravity = Gravity.TOP or Gravity.END
            params.x = 20 // Small margin from the edge
            val toolbar = requireActivity().findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
            if (toolbar != null) {
                params.y = toolbar.height
            }
            window.attributes = params

            // Set transparent background for rounded corners
            window.setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    companion object {
        const val TAG = "NotificationDialog"
        fun newInstance(): NotificationDialogFragment {
            return NotificationDialogFragment()
        }
    }
}

