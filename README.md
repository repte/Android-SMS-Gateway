# Android SMS Gateway 📱🚀

> [!WARNING]
> **Project Status: Alpha**
> This project is still in its early testing phase (Alpha). You might encounter bugs or times when the server is down. 
> **Official Go-Live Date: March 10th, 2026**

**Turn your Android phone into a high-performance HTTP bridge that sends and receives SMS for your smart home, bots, or custom apps!**

![App Interface](https://sms.ndemme.de/img/interface.png)

## 💡 Why use this over Twilio, Plivo, or Vonage/Nexmo?

If you are a hobby developer, home automation enthusiast, or independent maker, commercial SMS APIs are often overkill and incredibly frustrating to set up. **Android SMS Gateway** is built specifically to solve these headaches:

| Pain Point with Commercial APIs (Twilio) | How Android SMS Gateway Solves It |
| :--- | :--- |
| **💸 Per-Message Costs & Monthly Fees** | **Free & Flat-Rate:** Use your existing mobile plan! If you have a flat-rate SIM card, you can send unlimited SMS via the API for absolutely **$0** extra. No paying for virtual numbers. |
| **🛂 Strict KYC & Business Verification** | **No Red Tape:** To get a phone number on Twilio nowadays, you often need to provide business registration documents, passport copies, and jump through regulatory hoops. With this gateway, you just insert a prepaid SIM from the supermarket and you're online in 5 minutes. |
| **🚫 Sender ID Restrictions** | **Use a Real Number:** Many countries ban virtual numbers from sending certain types of messages, or force you to use a generic pre-registered text as the sender name. By using a physical Android phone, your messages come from a genuine, trusted mobile number. |
| **📥 Two-Way Communication is Expensive** | **Native Bi-Directional SMS:** You can receive SMS replies directly to your server instantly, allowing for powerful interactive bots and Home Assistant triggers without paying extra for incoming webhook routing. |

---

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
Go to our Developer Portal and register to get your personal, secret "API Key". You will need this key to link your phone to the server.
🔗 **<a href="https://sms.ndemme.de/" target="_blank">Click here to register a free account</a>**

### Step 3: Connect and Go!
1. Open the app on your phone.
2. Enter the **Default Server URL** (see above).
3. Enter your secret **API Key**.
4. Press the Start button! 

> [!TIP]
> **For the best performance:** We highly recommend enabling **Developer Mode** on your Android device and turning on the "Stay awake" setting (so the screen never turns off while charging). Keep the app running in the **foreground** and leave the phone plugged in to ensure it never goes to sleep and never misses an SMS!

Now you can use our simple API to send SMS from your own projects directly through your phone! You can find the full API documentation and copy-paste interactive examples inside the Web Dashboard.

## 📄 License
This project is open-source and free to use under the [MIT License](LICENSE).

---
*Built with ❤️ for everyday makers and developers.*
