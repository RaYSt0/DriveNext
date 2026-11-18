package ru.student.drivenext.presentation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import ru.student.drivenext.R

class OnboardingSlideFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_onboarding_slide, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val position = requireArguments().getInt(ARG_POSITION).coerceIn(0, 2)
        val titles = arrayOf("Аренда автомобилей", "Безопасно и удобно", "Лучшие предложения")
        val descriptions = arrayOf(
            "Открой для себя удобный и доступный способ передвижения",
            "Арендуй автомобиль и наслаждайся поездкой",
            "Выбирай понравившееся среди сотен доступных автомобилей"
        )
        view.findViewById<TextView>(R.id.slideTitle).text = titles[position]
        view.findViewById<TextView>(R.id.slideDescription).text = descriptions[position]
        view.findViewById<IllustrationView>(R.id.slideIllustration).scene = position + 1
    }

    companion object {
        private const val ARG_POSITION = "position"
        fun newInstance(position: Int) = OnboardingSlideFragment().apply {
            arguments = Bundle().apply { putInt(ARG_POSITION, position) }
        }
    }
}
