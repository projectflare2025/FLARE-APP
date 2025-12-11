package com.example.flare_capstone.views.fragment.unit

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.example.flare_capstone.R
import com.example.flare_capstone.databinding.DialogUnitDeploymentChatBinding

class UnitDeploymentChatDialogFragment : DialogFragment() {

    private var _binding: DialogUnitDeploymentChatBinding? = null
    private val binding get() = _binding!!

    private var purpose: String? = null
    private var date: String? = null

    companion object {
        private const val ARG_PURPOSE = "arg_purpose"
        private const val ARG_DATE = "arg_date"

        fun newInstance(purpose: String, date: String): UnitDeploymentChatDialogFragment {
            val args = Bundle().apply {
                putString(ARG_PURPOSE, purpose)
                putString(ARG_DATE, date)
            }
            return UnitDeploymentChatDialogFragment().apply {
                arguments = args
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ✅ No fullscreen theme, just a normal dialog without title
        setStyle(STYLE_NO_TITLE, 0)

        arguments?.let {
            purpose = it.getString(ARG_PURPOSE)
            date = it.getString(ARG_DATE)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogUnitDeploymentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Header title/subtitle
        binding.tvDialogTitle.text = "Deployment Chat"
        binding.tvDialogSubtitle.text = "${purpose ?: "Deployment"} • ${date ?: ""}"

        // Close button
        binding.btnClose.setOnClickListener { dismiss() }

        // Static sample messages
        addSampleMessages()

        // Send button: static behavior for now
        binding.sendButton.setOnClickListener {
            val text = binding.messageInput.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(requireContext(), "Type a message first (static demo)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            addMessageBubble(text, fromMe = true)
            binding.messageInput.text?.clear()
        }
    }

    private fun addSampleMessages() {
        addMessageBubble(
            "Unit 01, this is Central Command. We’ve received a confirmed report of a structural fire near Purok 7. " +
                    "Please proceed to the location immediately and provide a status update once you arrive.",
            fromMe = false
        )

        addMessageBubble(
            "Copy Central Command. Unit 01 is now mobilizing. We’re en route to the reported location. " +
                    "ETA is approximately 4 minutes depending on traffic conditions.",
            fromMe = true
        )

        addMessageBubble(
            "Acknowledged. Please exercise caution. Initial callers mentioned heavy smoke coming from the east side of the structure. " +
                    "Advise once visual confirmation is made.",
            fromMe = false
        )

        addMessageBubble(
            "Central Command, Unit 01 has visual now. Significant smoke observed; flames visible from the second floor. " +
                    "Requesting backup support and EMS standby.",
            fromMe = true
        )

        addMessageBubble(
            "Backup and EMS teams have been notified and are now being dispatched. Maintain safe distance until full team arrives. " +
                    "Keep communication open and provide updates every two minutes.",
            fromMe = false
        )
    }


    private fun addMessageBubble(text: String, fromMe: Boolean) {
        val ctx = requireContext()
        val tv = TextView(ctx).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.WHITE)
            setPadding(32, 24, 32, 24)
            background = resources.getDrawable(
                if (fromMe) R.drawable.sent_message_bg else R.drawable.received_message_bg,
                null
            )
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8
                bottomMargin = 4
                gravity = if (fromMe) android.view.Gravity.END else android.view.Gravity.START
            }
            layoutParams = params
            maxWidth = (resources.displayMetrics.widthPixels * 0.7f).toInt()
        }

        binding.chatMessagesContainer.addView(tv)

        binding.scrollView.post {
            binding.scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { w ->
            // ✅ Make dialog smaller & centered with transparent background
            val width = (resources.displayMetrics.widthPixels * 0.9f).toInt()
            w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            w.setBackgroundDrawableResource(android.R.color.transparent)
            w.setGravity(Gravity.CENTER)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
