# SAMAR — Personal AI Assistant (MVP 1.1)

Tony Stark style Android AI companion.

**Phone pe GitHub.com se kaam kar rahe ho? Neeche wala section padho.**

---

## GitHub Actions se APK kaise banaye (PC nahi chahiye)

1. GitHub pe **naya repository** banao (Public recommended for easy Actions).
2. Is poore `SAMAR` folder ke saare files upload karo.
   - Easy way: ZIP download karke, extract karke, GitHub pe "Upload files" se saari files daal do.
3. Repository ke **Actions** tab pe jao.
4. Left side mein **Build SAMAR APK** workflow dikhega.
5. **Run workflow** button dabao (ya code push karte hi auto start ho jayega).
6. Build complete hone ke baad neeche **Artifacts** section mein **SAMAR-APK** dikhega.
7. Uspe click karke APK download kar lo.
8. Phone pe install karo (pehle purana SAMAR uninstall kar dena).

**Pehli baar build 3–7 minute le sakta hai.**

Agar build fail ho jaye toh Actions ke log ka screenshot / error mujhe bhej dena.

---

## Backend URL (optional)

Jab real AI backend ready ho, is file mein URL change karna:

`app/src/main/java/com/samar/jarviswidget/MainActivity.kt`

```kotlin
private val BACKEND_URL = "https://tumhara-live-backend-url/api/samar/chat"
```

Jab tak real URL nahi daalte, app **local fallback** mode mein chalega — UI, voice, memory sab test kar sakte ho.

---

## Is build mein kya hai

- Arc Reactor style improved HUD
- Home screen widget ab **SAMAR** dikhata hai (JARVIS nahi)
- Voice input (mic + core tap)
- Text-to-Speech
- Text chat
- Persistent memory
- KAVACH settings panel
- Live battery + clock
- Terminal log stream

---

SAMAR Project • Built for Krishna
