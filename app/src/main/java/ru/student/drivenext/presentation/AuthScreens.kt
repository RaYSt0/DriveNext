package ru.student.drivenext.presentation

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Typeface
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import ru.student.drivenext.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AuthScreens(
    private val context: Context,
    private val viewModel: MainViewModel,
    private val choosePhoto: (PhotoKind) -> Unit,
    private val googleSignIn: () -> Unit
) {
    private val ink get() = context.getColor(R.color.ink)
    private val muted get() = context.getColor(R.color.muted)
    private val width get() = minOf(context.resources.displayMetrics.widthPixels - dp(48), dp(480))
    private val photoPreviews = mutableMapOf<PhotoKind, ImageView>()
    private val photoCaptions = mutableMapOf<PhotoKind, TextView>()
    private var updatePhotoButton: (() -> Unit)? = null
    private var backAction: (() -> Unit)? = null

    fun handleBack() { backAction?.invoke() }

    fun create(screen: Screen): View = when (screen) {
        Screen.AUTH -> choice()
        Screen.LOGIN -> login()
        Screen.REGISTER_ONE -> stepOne()
        Screen.REGISTER_TWO -> stepTwo()
        Screen.REGISTER_THREE -> stepThree()
        Screen.SUCCESS -> success()
        Screen.HOME -> home()
        else -> error("Нет формы для экрана $screen")
    }

    private fun choice(): View {
        backAction = null
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(44), dp(24), dp(24))
        }
        val content = column().apply {
            addView(title("DriveNext", 28))
            addView(text(context.getString(R.string.tagline), 15, muted).apply { top(4) })
        }
        root.addView(content, LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(IllustrationView(context).apply { scene = 0 },
            LinearLayout.LayoutParams(width, 0, 1f))
        val actions = column()
        actions.addView(button("Войти") { viewModel.showLogin() })
        actions.addView(button("Зарегистрироваться", outlined = true) { viewModel.showRegistration() }.apply { top(12) })
        root.addView(actions, LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT))
        return root
    }

    private fun login(): View {
        val page = FormPage("Вход в аккаунт", null) { viewModel.showAuthChoice() }
        val email = page.input("Электронная почта", "name@domain.com", inputType = 33)
        val password = page.password("Пароль")
        val login = button("Войти") { viewModel.submitLogin(email.text.toString(), password.text.toString()) }
        login.isEnabled = false
        fun update() { login.isEnabled = email.text.isNotBlank() && password.text.isNotBlank(); login.alpha = if (login.isEnabled) 1f else .5f }
        watch(email, ::update); watch(password, ::update)
        update()
        page.actions.addView(login)
        page.actions.addView(button("Войти через Google", outlined = true, action = googleSignIn).apply { top(12) })
        page.actions.addView(link("Нет аккаунта? Зарегистрироваться") { viewModel.showRegistration() }.apply { top(16) })
        return page.root
    }

    private fun stepOne(): View {
        val data = viewModel.registration
        lateinit var email: EditText
        lateinit var password: EditText
        lateinit var terms: CheckBox
        val page = FormPage("Регистрация", "Шаг 1 из 3 · Учётные данные") {
            viewModel.saveStepOne(email.text.toString(), password.text.toString(), terms.isChecked)
        }
        email = page.input("Электронная почта *", "name@domain.com", data.email, 33)
        password = page.password("Придумайте пароль *", data.password)
        val confirmation = page.password("Повторите пароль *", data.password)
        terms = CheckBox(context).apply {
            text = "Согласен(на) с условиями обслуживания и политикой конфиденциальности"
            setTextColor(ink)
            textSize = 14f
            isChecked = viewModel.termsAccepted
            top(14)
        }
        page.body.addView(terms)
        page.actions.addView(button("Далее") {
            viewModel.submitStepOne(email.text.toString(), password.text.toString(),
                confirmation.text.toString(), terms.isChecked)
        })
        page.actions.addView(link("Назад") {
            viewModel.saveStepOne(email.text.toString(), password.text.toString(), terms.isChecked)
        }.apply { top(12) })
        return page.root
    }

    private fun stepTwo(): View {
        val data = viewModel.registration
        lateinit var surname: EditText
        lateinit var name: EditText
        lateinit var patronymic: EditText
        lateinit var birthDate: EditText
        lateinit var gender: RadioGroup
        fun selectedGender() = (gender.findViewById<RadioButton>(gender.checkedRadioButtonId))?.text?.toString().orEmpty()
        val page = FormPage("Регистрация", "Шаг 2 из 3 · Личные данные") {
            viewModel.saveStepTwo(surname.text.toString(), name.text.toString(), patronymic.text.toString(),
                birthDate.text.toString(), selectedGender())
        }
        surname = page.input("Фамилия *", "Введите фамилию", data.surname)
        name = page.input("Имя *", "Введите имя", data.name)
        patronymic = page.input("Отчество", "При наличии", data.patronymic)
        birthDate = page.date("Дата рождения *", "MM/DD/YYYY", data.birthDate, "MM/dd/yyyy", true)
        page.body.addView(label("Пол *").apply { top(16) })
        gender = RadioGroup(context).apply {
            orientation = RadioGroup.HORIZONTAL
            addView(RadioButton(context).apply { id = View.generateViewId(); text = "Мужской"; isChecked = data.gender == "Мужской" })
            addView(RadioButton(context).apply { id = View.generateViewId(); text = "Женский"; isChecked = data.gender == "Женский" })
        }
        page.body.addView(gender)
        val next = button("Далее") {
            viewModel.submitStepTwo(surname.text.toString(), name.text.toString(), patronymic.text.toString(),
                birthDate.text.toString(), selectedGender())
        }
        fun update() {
            next.isEnabled = surname.text.isNotBlank() && name.text.isNotBlank() &&
                birthDate.text.isNotBlank() && gender.checkedRadioButtonId != -1
            next.alpha = if (next.isEnabled) 1f else .5f
        }
        listOf(surname, name, birthDate).forEach { watch(it, ::update) }
        gender.setOnCheckedChangeListener { _, _ -> update() }
        update()
        page.actions.addView(next)
        page.actions.addView(link("Назад") {
            viewModel.saveStepTwo(surname.text.toString(), name.text.toString(), patronymic.text.toString(),
                birthDate.text.toString(), selectedGender())
        }.apply { top(12) })
        return page.root
    }

    private fun stepThree(): View {
        val data = viewModel.registration
        lateinit var licenseNumber: EditText
        lateinit var issueDate: EditText
        val page = FormPage("Регистрация", "Шаг 3 из 3 · Документы") {
            viewModel.saveStepThree(licenseNumber.text.toString(), issueDate.text.toString())
        }
        photoPreviews.clear(); photoCaptions.clear()
        page.body.addView(photoRow(PhotoKind.AVATAR, "Фото профиля", false))
        licenseNumber = page.input("Номер водительского удостоверения *", "10 символов", data.licenseNumber)
        licenseNumber.filters = arrayOf(InputFilter.LengthFilter(10))
        issueDate = page.date("Дата выдачи *", "DD/MM/YYYY", data.issueDate, "dd/MM/yyyy", false)
        page.body.addView(photoRow(PhotoKind.LICENSE, "Фото водительского удостоверения *", true))
        page.body.addView(photoRow(PhotoKind.PASSPORT, "Фото паспорта *", true))
        val next = button("Далее") { viewModel.submitStepThree(licenseNumber.text.toString(), issueDate.text.toString()) }
        fun update() {
            next.isEnabled = licenseNumber.text.isNotBlank() && issueDate.text.isNotBlank() &&
                viewModel.registration.licenseUri != null && viewModel.registration.passportUri != null
            next.alpha = if (next.isEnabled) 1f else .5f
        }
        watch(licenseNumber, ::update); watch(issueDate, ::update)
        updatePhotoButton = ::update
        page.actions.addView(next)
        page.actions.addView(link("Назад") {
            viewModel.saveStepThree(licenseNumber.text.toString(), issueDate.text.toString())
        }.apply { top(12) })
        return page.root
    }

    fun refreshPhotos() {
        for ((kind, image) in photoPreviews) {
            val uri = when (kind) {
                PhotoKind.AVATAR -> viewModel.registration.avatarUri
                PhotoKind.LICENSE -> viewModel.registration.licenseUri
                PhotoKind.PASSPORT -> viewModel.registration.passportUri
            }
            if (uri != null) {
                runCatching {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver,
                        android.net.Uri.parse(uri))) { decoder, info, _ ->
                        decoder.setTargetSampleSize(maxOf(1, maxOf(info.size.width, info.size.height) / 256))
                    }
                }.onSuccess(image::setImageBitmap)
                photoCaptions[kind]?.text = "Фото выбрано · Изменить"
            }
        }
        updatePhotoButton?.invoke()
    }

    private fun photoRow(kind: PhotoKind, label: String, required: Boolean): View {
        val holder = column().apply { top(18) }
        holder.addView(label(label))
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; top(8) }
        val preview = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72))
            setImageResource(R.drawable.ic_splash)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = context.getDrawable(R.drawable.bg_input)
            setOnClickListener { choosePhoto(kind) }
            contentDescription = label
        }
        photoPreviews[kind] = preview
        row.addView(preview)
        val caption = link(if (required) "Загрузить фото" else "Добавить фото") { choosePhoto(kind) }
        (caption.layoutParams as? LinearLayout.LayoutParams)?.marginStart = dp(12)
        row.addView(caption, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(14) })
        photoCaptions[kind] = caption
        holder.addView(row)
        return holder
    }

    private fun success(): View {
        backAction = null
        val root = column().apply { gravity = Gravity.CENTER; setPadding(dp(24), dp(24), dp(24), dp(24)) }
        root.addView(ImageView(context).apply { setImageResource(R.drawable.ic_splash) },
            LinearLayout.LayoutParams(dp(108), dp(108)))
        root.addView(title("Регистрация завершена!", 27).apply { gravity = Gravity.CENTER; top(24) })
        root.addView(text("Добро пожаловать в DriveNext", 16, muted).apply { gravity = Gravity.CENTER; top(12) })
        root.addView(button("Далее") { viewModel.finishSuccess() }.apply { top(36) },
            LinearLayout.LayoutParams(width, dp(54)))
        return root
    }

    private fun home(): View {
        backAction = null
        val root = column().apply { gravity = Gravity.CENTER; setPadding(dp(24), dp(24), dp(24), dp(24)) }
        root.addView(title("DriveNext", 30).apply { gravity = Gravity.CENTER })
        root.addView(text("Ваша следующая поездка начинается здесь", 16, muted).apply { gravity = Gravity.CENTER; top(12) })
        root.addView(button("Выйти из аккаунта", outlined = true) { viewModel.logout() }.apply { top(36) },
            LinearLayout.LayoutParams(width, dp(54)))
        return root
    }

    private inner class FormPage(titleText: String, step: String?, onBack: () -> Unit) {
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val body = column()
        val actions = column()
        init {
            backAction = onBack
            val scroll = ScrollView(context).apply { isFillViewport = true }
            val wrap = column().apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(24), dp(16), dp(24), dp(24)) }
            body.addView(link("‹  Назад", onBack).apply { gravity = Gravity.START })
            body.addView(title(titleText, 27).apply { top(26) })
            if (step != null) body.addView(text(step, 15, muted).apply { top(6) })
            wrap.addView(body, LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT))
            scroll.addView(wrap)
            root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            val actionWrap = column().apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(24), dp(8), dp(24), dp(24)) }
            actionWrap.addView(actions, LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT))
            root.addView(actionWrap)
        }

        fun input(label: String, hint: String, value: String = "", inputType: Int = 1): EditText {
            body.addView(label(label).apply { top(18) })
            val field = EditText(context).apply {
                this.hint = hint
                setText(value)
                this.inputType = inputType
                textSize = 16f
                setSingleLine(true)
                setTextColor(ink)
                background = context.getDrawable(R.drawable.bg_input)
                setPadding(dp(14), 0, dp(14), 0)
                top(8)
            }
            body.addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
            return field
        }

        fun password(label: String, value: String = ""): EditText {
            body.addView(label(label).apply { top(18) })
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = context.getDrawable(R.drawable.bg_input)
                top(8)
            }
            val field = EditText(context).apply {
                hint = "Введите пароль"
                setText(value)
                inputType = 129
                transformationMethod = PasswordTransformationMethod.getInstance()
                setSingleLine(true)
                textSize = 16f
                setTextColor(ink)
                background = null
                setPadding(dp(14), 0, 0, 0)
            }
            row.addView(field, LinearLayout.LayoutParams(0, dp(54), 1f))
            row.addView(link("Показать") {
                val shown = field.transformationMethod == null
                field.transformationMethod = if (shown) PasswordTransformationMethod.getInstance() else null
                field.setSelection(field.text.length)
            }.apply { setPadding(dp(8), 0, dp(14), 0) })
            body.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
            return field
        }

        fun date(label: String, hint: String, value: String, format: String, birth: Boolean): EditText {
            val field = input(label, hint, value)
            field.isFocusable = false
            field.isClickable = true
            field.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.ic_menu_my_calendar, 0)
            field.setOnClickListener {
                val calendar = Calendar.getInstance()
                if (birth) calendar.add(Calendar.YEAR, -18)
                DatePickerDialog(context, { _, year, month, day ->
                    val selected = Calendar.getInstance().apply { set(year, month, day) }
                    field.setText(SimpleDateFormat(format, Locale.US).format(selected.time))
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).apply {
                    datePicker.maxDate = System.currentTimeMillis()
                }.show()
            }
            return field
        }
    }

    private fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private fun label(value: String) = text(value, 15, ink).apply { setTypeface(null, Typeface.BOLD) }
    private fun title(value: String, size: Int) = text(value, size, ink).apply { setTypeface(null, Typeface.BOLD) }
    private fun text(value: String, size: Int, color: Int) = TextView(context).apply {
        text = value
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size.toFloat())
    }
    private fun link(value: String, action: () -> Unit) = text(value, 15, ink).apply {
        gravity = Gravity.CENTER
        minHeight = dp(44)
        setOnClickListener { action() }
    }
    private fun button(value: String, outlined: Boolean = false, action: () -> Unit) = text(value, 16,
        if (outlined) ink else Color.WHITE).apply {
        gravity = Gravity.CENTER
        minHeight = dp(54)
        background = context.getDrawable(if (outlined) R.drawable.bg_outline_button else R.drawable.bg_primary_button)
        setOnClickListener { action() }
    }
    private fun watch(field: EditText, action: () -> Unit) {
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = action()
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }
    private fun View.top(value: Int) {
        // Margin is set when the view is attached by the caller's LinearLayout.
        post {
            (layoutParams as? LinearLayout.LayoutParams)?.let {
                it.topMargin = dp(value)
                layoutParams = it
            }
        }
    }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
