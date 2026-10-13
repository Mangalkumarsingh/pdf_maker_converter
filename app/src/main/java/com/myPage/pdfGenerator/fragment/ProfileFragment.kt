package com.myPage.pdfGenerator.fragment

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.myPage.pdfGenerator.Activity.PrivacyPolicyActivity
import com.myPage.pdfGenerator.R
import kotlin.random.Random

class ProfileFragment : Fragment() {


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        val tvUserName = view.findViewById<TextView>(R.id.tvUserName)
        val tvSubscription = view.findViewById<TextView>(R.id.tvSubscription)

        val llPrivacyPolicy = view.findViewById<LinearLayout>(R.id.llPrivacyPolicy)
        val llShareApp = view.findViewById<LinearLayout>(R.id.llShareApp)
        val llRateUs = view.findViewById<LinearLayout>(R.id.llRateUs)
        val randomUserId = generateRandomUserId()

        tvUserName.text = randomUserId

        tvSubscription.text = "Subscription: Free"

        llPrivacyPolicy.setOnClickListener {
            var intent= Intent(requireContext(), PrivacyPolicyActivity::class.java)
           startActivity(intent)
        }


        llShareApp?.setOnClickListener {
            val appPackageName = requireContext().packageName
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "PDF Generator App")


            val shareMessage = "Hey! Check out this awesome PDF Generator App. It converts Docs & Images to PDF easily!\n\nDownload it here: https://play.google.com/store/apps/details?id=$appPackageName"
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareMessage)

            startActivity(Intent.createChooser(shareIntent, "Share App via"))
        }


        llRateUs?.setOnClickListener {
            val appPackageName = requireContext().packageName
            try {

                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackageName")))
            } catch (e: ActivityNotFoundException) {

                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")))
            }
        }

        return view
    }
    private fun generateRandomUserId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val randomString = (1..6)
            .map { chars.random() }
            .joinToString("")
        return "User_$randomString" // Output example: User_K8B3X9
    }
}