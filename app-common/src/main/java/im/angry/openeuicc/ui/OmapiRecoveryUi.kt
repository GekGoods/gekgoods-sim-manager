package im.angry.openeuicc.ui

import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import im.angry.openeuicc.OpenEuiccApplication
import im.angry.openeuicc.common.R
import im.angry.openeuicc.core.EuiccChannelFactory

/** Shared by both privileged and unprivileged empty-state screens. */
fun Fragment.bindOmapiRecoveryUi(view: View, compatibilityButton: View? = null) {
    val factory = (requireContext().applicationContext as OpenEuiccApplication)
        .appContainer.euiccChannelFactory
    if (factory.isOmapiQuarantined) {
        view.findViewById<TextView>(R.id.no_euicc_placeholder)
            .setText(R.string.omapi_timeout_requires_reboot)
    }
    view.findViewById<View>(R.id.omapi_recovery).apply {
        visibility = if (factory.isOmapiQuarantined) View.VISIBLE else View.GONE
        setOnClickListener {
            showOmapiRecoveryDialog(factory) { requireActivity().recreate() }
        }
    }
    compatibilityButton?.visibility =
        if (factory.isOmapiQuarantined) View.GONE else View.VISIBLE
}

fun Fragment.showOmapiRecoveryDialog(factory: EuiccChannelFactory, onResumed: () -> Unit) {
    AlertDialog.Builder(requireContext())
        .setTitle(R.string.omapi_recovery_title)
        .setMessage(R.string.omapi_recovery_warning)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.omapi_recovery_confirm) { _, _ ->
            if (factory.tryResumeOmapiAfterTimeout()) onResumed()
        }
        .show()
}
