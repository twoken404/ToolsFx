package me.leon.view

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.control.*
import me.leon.CHARSETS
import me.leon.SimpleMsgEvent
import me.leon.controller.EncodeController
import me.leon.encode.base.base64
import me.leon.ext.*
import tornadofx.*
import tornadofx.FX.Companion.messages

class EncodeView : View(messages["encodeAndDecode"]) {
    private val controller: EncodeController by inject()
    override val closeable = SimpleBooleanProperty(false)
    private val isSingleLine = SimpleBooleanProperty(false)
    private val decodeIgnoreSpace = SimpleBooleanProperty(true)
    private lateinit var taInput: TextArea
    private lateinit var taOutput: TextArea
    private lateinit var labelInfo: Label
    private lateinit var tfCustomDict: TextField
    private lateinit var tfCount: TextField
    private var enableDict = SimpleBooleanProperty(true)
    private val info: String
        get() =
            "${if (isEncode) messages["encode"] else messages["decode"]}: $encodeType  ${messages["inputLength"]}:" +
                    " ${inputText.length}  ${messages["outputLength"]}: ${outputText.length}"
    private val inputText: String
        get() =
            taInput.text.takeIf {
                isEncode || encodeType in arrayOf(EncodeType.Decimal, EncodeType.Octal)
            }
                ?: taInput.text.takeUnless { decodeIgnoreSpace.get() }
                ?: taInput.text.replace("\\s".toRegex(), "")
    private val outputText: String
        get() = taOutput.text

    private var encodeType = EncodeType.Base64
    private var isEncode = true
    private val selectedCharset = SimpleStringProperty(CHARSETS.first())

    private val eventHandler = fileDraggedHandler { taInput.text = it.first().readText() }

    private val encodeTypeWithSpace =
        arrayOf(
            EncodeType.UuEncode,
            EncodeType.XxEncode,
            EncodeType.QuotePrintable,
            EncodeType.PunyCode,
        )

    private val centerNode = vbox {
        paddingAll = DEFAULT_SPACING
        spacing = DEFAULT_SPACING
        hbox {
            label(messages["input"])
            button(graphic = imageview("/img/import.png")) {
                action { taInput.text = clipboardText() }
            }
        }

        taInput =
            textarea {
                promptText = messages["inputHint"]
                isWrapText = true
                onDragEntered = eventHandler
                contextmenu {
                    item(messages["loadFromNet"]) {
                        action { runAsync { inputText.readFromNet() } ui { taInput.text = it } }
                    }
                    item(messages["loadFromNetLoop"]) {
                        action {
                            runAsync { inputText.simpleReadFromNet() } ui { taInput.text = it }
                        }
                    }
                    item(messages["loadFromNet2"]) {
                        action {
                            runAsync { inputText.readBytesFromNet().base64() } ui
                                    {
                                        taInput.text = it
                                    }
                        }
                    }
                    item(messages["readHeadersFromNet"]) {
                        action {
                            runAsync { inputText.readHeadersFromNet() } ui { taInput.text = it }
                        }
                    }
                }
            }
        hbox {
            alignment = Pos.CENTER_LEFT
            paddingTop = DEFAULT_SPACING
            paddingBottom = DEFAULT_SPACING
            spacing = DEFAULT_SPACING
            label("${messages["encode"]}:")
            tilepane {
                vgap = 8.0
                alignment = Pos.TOP_LEFT
                prefColumns = 7
                togglegroup {
                    encodeTypeMap.forEach {
                        radiobutton(it.key) {
                            setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE)
                            if (it.value == EncodeType.Base64) isSelected = true
                        }
                    }
                    selectedToggleProperty().addListener { _, _, new ->
                        encodeType = new.cast<RadioButton>().text.encodeType()
                        enableDict.value = encodeType.type.contains("base")
                        tfCustomDict.text = encodeType.defaultDict

                        println()
                        val isIgnore = encodeType !in encodeTypeWithSpace
                        decodeIgnoreSpace.set(isIgnore)
                        println("${decodeIgnoreSpace.get()} $isIgnore")
                        if (isEncode) run()
                    }
                }
            }
        }

        hbox {
            label(messages["customDict"])
            alignment = Pos.BASELINE_LEFT
            tfCustomDict =
                textfield(encodeType.defaultDict) {
                    enableWhen { enableDict }
                    prefWidth = DEFAULT_SPACING_80X
                }
        }

        hbox {
            spacing = DEFAULT_SPACING
            alignment = Pos.CENTER
            togglegroup {
                spacing = DEFAULT_SPACING
                alignment = Pos.CENTER
                label("charset:")
                combobox(selectedCharset, CHARSETS) { cellFormat { text = it } }

                radiobutton(messages["encode"]) { isSelected = true }
                radiobutton(messages["decode"])
                checkbox(messages["singleLine"], isSingleLine) {
                    selectedProperty().addListener { _, _, newValue ->
                        decodeIgnoreSpace.set(!newValue)
                    }
                }
                checkbox(messages["decodeIgnoreSpace"], decodeIgnoreSpace) {
                    selectedProperty().addListener { observable, oldValue, newValue ->
                        println("$observable $oldValue  $newValue")
                    }
                }

                label("times:")
                tfCount = textfield("1") {
                    prefWidth = DEFAULT_SPACING_8X
                }
                selectedToggleProperty().addListener { _, _, new ->
                    isEncode = new.cast<RadioButton>().text == messages["encode"]
                    run()
                }
            }
            button(messages["run"], imageview("/img/run.png")) { action { run() } }
        }
        hbox {
            spacing = DEFAULT_SPACING
            label(messages["output"])
            button(graphic = imageview("/img/copy.png")) { action { outputText.copy() } }
            button(graphic = imageview("/img/up.png")) {
                action {
                    taInput.text = outputText
                    taOutput.text = ""
                }
            }
            button(graphic = imageview("/img/jump.png")) {
                action {
                    var tmp: Parent? = parent
                    while (tmp != null) {
                        if (tmp is TabPane) break
                        tmp = tmp.parent
                    }
                    tmp.safeAs<TabPane>()?.selectionModel?.select(2)
                }
            }
        }

        taOutput =
            textarea {
                promptText = messages["outputHint"]
                isWrapText = true
            }
    }
    override val root = borderpane {
        center = centerNode
        bottom = hbox { labelInfo = label(info) }
    }

    private fun run() {

        var result =inputText
        repeat(tfCount.text.toInt()) {
            result = if (isEncode)
                controller.encode2String(
                    result,
                    encodeType,
                    tfCustomDict.text,
                    selectedCharset.get(),
                    isSingleLine.get()
                )
            else
                controller.decode2String(
                    result,
                    encodeType,
                    tfCustomDict.text,
                    selectedCharset.get(),
                    isSingleLine.get()
                )
        }

        taOutput.text = result
        if (Prefs.autoCopy) outputText.copy().also { primaryStage.showToast(messages["copied"]) }
        labelInfo.text = info

        fire(SimpleMsgEvent(taOutput.text, 1))
    }
}
