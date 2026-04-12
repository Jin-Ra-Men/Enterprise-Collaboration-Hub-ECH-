; CSTalk NSIS hook — remove legacy "ECH" install (productName ECH, appId com.ech.desktop)
; before installing this product. Covers per-machine (Program Files) and per-user (LocalAppData\Programs).
; Custom install dir: remove legacy once manually or extend this script.

!macro customInit
  ExecWait 'cmd.exe /c taskkill /F /IM ECH.exe /T' $R9

  IfFileExists "$PROGRAMFILES64\ECH\Uninstall ECH.exe" legacy_uninst64 legacy_try_pf32
  legacy_uninst64:
    DetailPrint "Removing legacy ECH (silent uninstall, Program Files x64)..."
    ExecWait '"$PROGRAMFILES64\ECH\Uninstall ECH.exe" /S' $R9
    Goto legacy_done

  legacy_try_pf32:
  IfFileExists "$PROGRAMFILES\ECH\Uninstall ECH.exe" legacy_uninst32 legacy_try_local
  legacy_uninst32:
    DetailPrint "Removing legacy ECH (silent uninstall, Program Files)..."
    ExecWait '"$PROGRAMFILES\ECH\Uninstall ECH.exe" /S' $R9
    Goto legacy_done

  legacy_try_local:
  IfFileExists "$LOCALAPPDATA\Programs\ECH\Uninstall ECH.exe" legacy_uninst_local legacy_done
  legacy_uninst_local:
    DetailPrint "Removing legacy ECH (silent uninstall, per-user)..."
    ExecWait '"$LOCALAPPDATA\Programs\ECH\Uninstall ECH.exe" /S' $R9

  legacy_done:
!macroend
