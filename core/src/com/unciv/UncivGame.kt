package com.unciv

import com.badlogic.gdx.Application
import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.utils.Align
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.logic.GameStarter
import com.unciv.logic.map.MapParameters
import com.unciv.models.metadata.GameParameters
import com.unciv.models.metadata.GameSettings
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.translations.Translations
import com.unciv.ui.LanguagePickerScreen
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.center
import com.unciv.ui.worldscreen.WorldScreen
import java.util.*
import kotlin.concurrent.thread

class UncivGame(val version: String) : Game() {
    lateinit var gameInfo: GameInfo
    lateinit var settings : GameSettings
    /**
     * This exists so that when debugging we can see the entire map.
     * Remember to turn this to false before commit and upload!
     */
    var viewEntireMapForDebug = false

    /** For when you need to test something in an advanced game and don't have time to faff around */
    val superchargedForDebug = false

    lateinit var worldScreen: WorldScreen

    var music : Music? =null
    val musicLocation = "music/thatched-villagers.mp3"
    var isInitialized = false
    var rewriteTranslationFiles = false

    lateinit var ruleset:Ruleset

    val translations = Translations()

    override fun create() {
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        if (Gdx.app.type != Application.ApplicationType.Desktop)
            viewEntireMapForDebug = false
        Current = this


        // If this takes too long players, especially with older phones, get ANR problems.
        // Whatever needs graphics needs to be done on the main thread,
        // So it's basically a long set of deferred actions.
        // We probably could make this better by moving stuff that we can to another thread but ehhhhhhh
        settings = GameSaver().getGeneralSettings() // needed for the screen
        screen=LoadingScreen()
        thread {
            ruleset = Ruleset(true)

            if(rewriteTranslationFiles) { // Yes, also when running from the Jar. Sue me.
                translations.readAllLanguagesTranslation()
                translations.writeNewTranslationFiles()
            }
            else{
                translations.tryReadTranslationForCurrentLanguage()
            }

            if (settings.userId == "") { // assign permanent user id
                settings.userId = UUID.randomUUID().toString()
                settings.save()
            }
            Gdx.app.postRunnable {
                CameraStageBaseScreen.resetFonts()
                if (GameSaver().getSave("Autosave").exists()) {
                    try {
                        loadGame("Autosave")
                    } catch (ex: Exception) { // silent fail if we can't read the autosave
                        startNewGame()
                    }
                } else setScreen(LanguagePickerScreen())

                thread { startMusic() }
                isInitialized = true
            }
        }
    }

    fun startMusic(){

        val musicFile = Gdx.files.local(musicLocation)
        if(musicFile.exists()){
            music = Gdx.audio.newMusic(musicFile)
            music!!.isLooping=true
            music!!.volume = 0.4f*settings.musicVolume
            music!!.play()
        }
    }

    fun setScreen(screen: CameraStageBaseScreen) {
        Gdx.input.inputProcessor = screen.stage
        super.setScreen(screen)
    }

    fun loadGame(gameInfo:GameInfo){
        this.gameInfo = gameInfo

        worldScreen = WorldScreen(gameInfo.getPlayerToViewAs())
        setWorldScreen()
    }

    fun loadGame(gameName:String){
        loadGame(GameSaver().loadGameByName(gameName))
    }

    fun startNewGame() {
        val newGame = GameStarter().startNewGame(GameParameters().apply { difficulty="Chieftain" }, MapParameters())
        loadGame(newGame)
    }

    fun setWorldScreen() {
        if(screen != null && screen != worldScreen) screen.dispose()
        setScreen(worldScreen)
        worldScreen.shouldUpdate=true // This can set the screen to the policy picker or tech picker screen, so the input processor must come before
    }

    override fun resume() {
        super.resume()
        ImageGetter.refreshAltas()

        // This is to solve a rare problem that I still don't understand its cause -
        // Sometimes, resume() is called and the gameInfo doesn't have any civilizations.
        // My guess is that resume() was called but create() wasn't, or perhaps was aborted too early,
        // and the original (and empty) initial GameInfo remained.
        if(!::gameInfo.isInitialized || gameInfo.civilizations.isEmpty())
            return create()

        if(::worldScreen.isInitialized) worldScreen.dispose() // I hope this will solve some of the many OuOfMemory exceptions...
        loadGame(gameInfo)
    }

    // Maybe this will solve the resume error on chrome OS, issue 322? Worth a shot
    override fun resize(width: Int, height: Int) {
        resume()
    }

    override fun dispose() {
        if(::gameInfo.isInitialized)
            GameSaver().autoSave(gameInfo)
    }

    companion object {
        lateinit var Current: UncivGame
    }
}

class LoadingScreen:CameraStageBaseScreen(){
    init{
        val happinessImage = ImageGetter.getImage("StatIcons/Happiness")
        happinessImage.center(stage)
        happinessImage.setOrigin(Align.center)
        happinessImage.addAction(Actions.sequence(
                Actions.delay(1f),
                Actions.rotateBy(360f,0.5f)))
        stage.addActor(happinessImage)
    }

}