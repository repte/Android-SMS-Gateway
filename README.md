# Android SMS Gateway 📱🚀

> [!WARNING]
> **Project Status: Alpha**
> This project is still in its early testing phase (Alpha). You might encounter bugs or times when the server is down. 
> **Official Go-Live Date: March 10th, 2026**

**Turn your Android phone into a bridge that sends and receives SMS for your smart home, bots, or apps!**

Do you have a project that needs to send real SMS text messages (like Home Assistant, a custom app, or a smart device)? Instead of paying expensive online services, you can just use your own Android phone and your own SIM card!

![App Interface](https://sms.ndemme.de/img/interface.png)

## 🌐 Default Server
If you don't want to host the server yourself, you can use our public testing server:
**`https://api-sms.ndemme.de/`**

> [!TIP]
> We are currently working on providing official Docker containers so that system administrators can easily self-host the server on their own infrastructure! Stay tuned.

## 🛠️ What do I need?
- **An Android Phone:** Any phone running Android 8.0 or newer.
- **A SIM Card:** With a plan that allows sending and receiving SMS.
- **Internet:** Wi-Fi or mobile data so the phone can talk to the server.
- **Power:** Leave the phone plugged into a charger so it never turns off!

## 🚀 How to set it up (Step-by-Step)

### Step 1: Install the App on your Phone
Download and install our app on your Android phone:
📥 **[Download the App here (APK)](https://sms.ndemme.de/img/app-release.apk)**

> [!NOTE]
> **Google Play Protect Warning:** Because this app is downloaded from the internet and not the official Google Play Store, your phone might show a "Play Protect" warning. This is a false positive alarm. If you want to be 100% sure the app is safe, you can download the source code from this Git repository and compile the app yourself!

### Step 2: Create a Free Account
Go to our website and register to get your personal, secret "API Key". You will need this key to link your phone to the server.
🔗 **<a href="https://sms.ndemme.de/" target="_blank">Click here to register a free account</a>**

### Step 3: Connect and Go!
1. Open the app on your phone.
2. Enter the **Default Server URL** (see above).
3. Enter your secret **API Key**.
4. Press the Start button! 

Now you can use our simple API to send SMS from your own projects directly through your phone!

## 📄 License
This project is open-source and free to use under the [MIT License](LICENSE).

---
*Built with ❤️ for everyday makers and developers.*
