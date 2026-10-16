package studio.cluvex.aethery.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import studio.cluvex.aethery.AppAppearance
import kotlin.math.roundToInt

class EditorField(val name: String, val input: EditText)

object EditorModel {
    /** The first validation error for [values], or null when they are acceptable. */
    fun firstError(
        values: List<String>,
        validator: ((List<String>) -> Pair<Int, String>?)?,
    ): Pair<Int, String>? = validator?.invoke(values)
}

/**
 * A page of text fields with Save pinned to the bottom. A field that fails validation shows its
 * message in place and shakes; nothing pops up over the page.
 */
fun buildEditorPage(
    context: Context,
    palette: AppAppearance.Palette,
    title: String,
    subtitle: String,
    fields: List<EditorField>,
    validator: ((List<String>) -> Pair<Int, String>?)?,
    secondaryAction: Pair<String, () -> Unit>?,
    onBack: () -> Unit,
    onSave: (List<String>) -> Unit,
): View {
    val density = context.resources.displayMetrics.density
    fun dp(value: Int) = (value * density).roundToInt()
    val reduced = Motion.reduced(context)

    fun button(text: String, filled: Boolean, onClick: () -> Unit) = TextView(context).apply {
        this.text = text
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(if (filled) palette.canvas else palette.ink)
        typeface = HaloType.body(context, medium = true)
        background = if (filled) {
            android.graphics.drawable.GradientDrawable().apply {
                setColor(palette.primary)
                cornerRadius = dp(16).toFloat()
            }
        } else {
            glassBackground(palette, 16, density)
        }
        isClickable = true
        isFocusable = true
        contentDescription = text
        setOnClickListener { onClick() }
    }

    val errors = ArrayList<TextView>()
    lateinit var saveBar: LinearLayout
    saveBar = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    val scaffold = buildScaffold(context, palette, title, subtitle, onBack, saveBar)

    fields.forEach { field ->
        scaffold.content.addView(TextView(context).apply {
            text = field.name
            textSize = 13f
            setTextColor(palette.muted)
            typeface = HaloType.body(context, medium = true)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(14)
            bottomMargin = dp(6)
        })
        field.input.apply {
            setTextColor(palette.ink)
            setHintTextColor(HaloColors.withAlpha(palette.muted, 0.7f))
            typeface = HaloType.body(context, medium = false)
            background = glassBackground(palette, 16, density)
            (parent as? ViewGroup)?.removeView(this)
        }
        scaffold.content.addView(field.input, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            if (field.input.maxLines > 1) dp(112) else dp(56),
        ))
        val error = TextView(context).apply {
            textSize = 13f
            setTextColor(palette.error)
            typeface = HaloType.body(context, medium = false)
            visibility = View.GONE
        }
        errors.add(error)
        scaffold.content.addView(error, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(6) })
        field.input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                error.visibility = View.GONE
            }
        })
    }

    if (secondaryAction != null) {
        saveBar.addView(
            button(secondaryAction.first, filled = false, onClick = secondaryAction.second),
            LinearLayout.LayoutParams(0, dp(52), 1f).apply { rightMargin = dp(10) },
        )
    }
    saveBar.addView(button("Save", filled = true) {
        val values = fields.map { it.input.text.toString().trim() }
        val problem = EditorModel.firstError(values, validator)
        if (problem != null) {
            val (index, message) = problem
            errors[index].text = message
            errors[index].visibility = View.VISIBLE
            Haptics.reject(fields[index].input)
            if (!reduced) {
                val input = fields[index].input
                val shake = SpringValue(0f, stiffness = 600f, damping = 0.3f) { input.translationX = it }
                shake.snapTo(-8f * density)
                shake.animateTo(0f)
            }
            return@button
        }
        onSave(values)
        onBack()
    }, LinearLayout.LayoutParams(0, dp(52), if (secondaryAction != null) 1f else 1f))
    return scaffold.page
}
