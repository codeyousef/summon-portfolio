package code.yousef.portfolio.i18n.strings

import code.yousef.portfolio.i18n.LocalizedText

object ContactStrings {
    // Contact section (full form)
    val title = LocalizedText("Collaborate", "تعاون")
    val subtitle = LocalizedText(
        en = "Tell me what you're building. I'll reply with a focused plan (and timelines) you can immediately act on.",
        ar = "أخبرني بما تعمل عليه وسأعود إليك بخطة واضحة وجدول زمني يمكن البدء به فورًا."
    )
    val lead = LocalizedText(
        en = "Average response time: under 24h. Share a sentence or two about your project and I'll follow up with a plan.",
        ar = "متوسط وقت الرد أقل من 24 ساعة. شارك سطرًا أو سطرين عن مشروعك وسأتواصل معك بخطة واضحة."
    )
    val contactMethods = LocalizedText(
        en = "Prefer WhatsApp or email? Use whichever is fastest — just include at least one so I can reply.",
        ar = "يمكنك استخدام البريد الإلكتروني أو رقم واتساب — فقط اذكر أحدهما على الأقل حتى أتمكن من الرد."
    )
    val formTitle = LocalizedText("Project details", "تفاصيل المشروع")
    val name = LocalizedText("Name", "الاسم")
    val email = LocalizedText("Email", "البريد الإلكتروني")
    val whatsapp = LocalizedText("WhatsApp", "رقم واتساب")
    val whatsappPlaceholder = LocalizedText("+966 565 123 4567", "+966 5X XXX XXXX")
    val requirements = LocalizedText("What are we building?", "ماذا سنبني؟")
    val optional = LocalizedText("Optional", "اختياري")
    val submit = LocalizedText("Send request", "أرسل الطلب")
    val submitting = LocalizedText("Sending...", "جاري الإرسال...")
    val success = LocalizedText("Thanks! I'll reply soon.", "شكرًا! سأتواصل معك قريبًا.")
    val failure = LocalizedText("Something went wrong. Please try again.", "حدث خطأ. حاول مرة أخرى.")
    val errorName = LocalizedText("Name is required", "الاسم مطلوب")
    val errorEmail = LocalizedText("Please enter a valid email", "يرجى إدخال بريد صحيح")
    val errorRequirements = LocalizedText("Share a bit about your project", "شارك تفاصيل حول مشروعك")

    // Contact footer (compact form)
    object Footer {
        val title = LocalizedText(
            en = "Let's Build Something",
            ar = "لنبنِ شيئًا معًا"
        )
        val subtitle = LocalizedText(
            en = "Have a project in mind? Drop me a message or book a call.",
            ar = "لديك مشروع في ذهنك؟ راسلني أو احجز مكالمة."
        )
        val contactLabel = LocalizedText(
            en = "Email or WhatsApp",
            ar = "البريد الإلكتروني أو واتساب"
        )
        val contactPlaceholder = LocalizedText(
            en = "you@example.com or +1 234 567 8900",
            ar = "you@example.com أو +966 5X XXX XXXX"
        )
        val messageLabel = LocalizedText(
            en = "How can I help?",
            ar = "كيف يمكنني مساعدتك؟"
        )
        val submit = LocalizedText(
            en = "Get in Touch",
            ar = "تواصل معي"
        )
        val bookingLink = LocalizedText(
            en = "Skip the back-and-forth. Book a discovery call →",
            ar = "تجنب المراسلات. احجز مكالمة تعريفية ←"
        )
    }
}
