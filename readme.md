# sekiro-save-saver
Automatic savegame backup manager. Made for Sekiro: Shadows Die Twice, but can manage all kinds of files.

## Why this app?
Sekiro: Shadows Die Twice crashes from time to time and when it does, it has a small chance of corrupting your savegame.
When this happens, you usually can only throw the savegame away and start from the beginning of the game.

This app automatically makes backups of your savegames every time the game writes them to the disk.
When the game corrupts your savegame, you can restore an older version of your savegame with one click.

# How to use

## Installation
1. download the latest release from [the release page](https://github.com/LostMekka/sekiro-save-saver/releases).
1. Extract the zip file into a directory of your choice
1. Make sure you have Java installed (at least Java 8)

If you are feeling adventurous, you can also build the app yourself.
All you need to do for that is check out the sources and run `./gradlew distZip`

## starting the App
1. Start the app by double-clicking `sekiro-save-saver.jar`
1. Chose your savegame directory.
   For Sekiro this probably will be `C:\Users\<yourWindowsUserName>\AppDataRoaming\Sekiro\<yourProfileId>`.
   The app tries to locate this directory for you, so it might already be selected.
   To choose, simply double click on any savegame file in the target directory.
1. start playing your game. You will hear a sound every time an automatic backup of a save file is made.
   (you can disable sounds in the config, see below)
   
## In case your game crashed
1. if your game crashed and your savegame is corrupted, select the corrupted file on the left of the main window.
   Then click the `Restore` button on the right side for the backup you wish to restore.
   A sound will signal that the backup was restored successfully.
   **Note:** it is recommended that you only restore savegames when the game is not running!
1. Start your game again and keep playing with your restored savegame.

## Configuration
The app comes with a configuration file named `config.properties`.
You can change the values contained in it to your liking, but you need to restart the app when you do.

These are the values you can set:

|Property name|Explanation|
|---|---|
|watchDir|The standard directory that the app will watch. You always will be asked to choose the directory when you start the app, but this will be the starting point for the file chooser dialog.|
|playSounds|Set this to "true" and the app will play sounds. Set it to "false" and lo sounds will be played.|
|maxBackupFileCount|The number of backup files the app will keep for each monitored file.|
|backupBlockTimeAfterBackupInMs|This is the "cooldown" for the file backup after making a backup. (in milliseconds) Set it to zero, if you want to have a backup every time your game is saving the file to disk.  A value of 2000 (2 seconds) is usually fine though.|
|backupBlockTimeAfterRestoreInMs|This is the "cooldown"for the file backup after restoring a backup. (in milliseconds) You should probably leave it as is.|

You can also replace the sounds in the `sounds` directory:
- `save.wav` is played when a backup is saved.
- `restore.wav` is played when a backup is restored.
- `error.wav` is played when there was an error. 
  (most of the time this will occur because the game is currently writing to the savegame file and the app cannot access it to make or restore a backup)
  
If you change the sounds, make sure the new ones are in the `.wav` format and are fairly small.
Bigger sound files may degrade app performance. It just is not made for that ;)

# Disclaimer
I am not affiliated with From Software or any game studio or publisher of the games you might use this app on. 
This app is provided as is, with no warranty.
I am not responsible for any loss of progress in your game, I just want to help :)

Also a note of caution: Please do not use this app to save-hop your way through the game.
I strongly discourage any other use of this app than to restore savegame files when they are corrupted and would be lost otherwise.
With that in mind: Happy gaming and praise the sun!
