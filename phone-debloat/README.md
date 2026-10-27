# 📱 Android Phone Debloater Toolkit

A safe, reversible OEM debloater and telemetry cleaner for Android smartphones (Samsung, Xiaomi, POCO, Redmi, OnePlus, Realme, Oppo, Vivo, Motorola, Pixel).

---

## 📁 Project Structure

```text
phone-debloat/
├── debloat.ps1        # Interactive Windows / PowerShell debloat & restore tool
├── debloat.sh         # Linux / macOS / Termux debloat shell script
├── bloat_database.txt # Curated bloatware package list by OEM
├── protected.txt      # Critical phone/telephony packages that are NEVER touched
└── backups/           # Auto-saved baseline package snapshots for 100% reversible restore
```

---

## 🚀 How to Use

### 1. Enable USB Debugging on your Phone
1. Go to **Settings** > **About Phone**.
2. Tap **Build Number** 7 times to enable **Developer Options**.
3. Go to **Developer Options** and turn ON **USB Debugging** (and **Install via USB** / **USB debugging (Security settings)** if on Xiaomi/MIUI).
4. Connect phone via USB or Wireless ADB (`adb connect <PHONE_IP>:5555`).

### 2. Run on Windows (PowerShell)
```powershell
cd phone-debloat
.\debloat.ps1
```

### 3. Run on Linux / macOS / Termux (Bash)
```bash
cd phone-debloat
chmod +x debloat.sh
./debloat.sh
```

---

## 🛡️ Safety & Restoration
- **Zero Bricking**: [`protected.txt`](file:///d:/GITHUB/apple-tv-launcher/phone-debloat/protected.txt) guarantees telephony, SystemUI, dialer, cellular, and Google Play services are never uninstalled.
- **Automatic Snapshots**: A timestamped baseline snapshot of all installed and disabled packages is saved to `backups/` before any action.
- **1-Click Restore**: Uninstalled apps can be restored anytime via Option 4 (`cmd package install-existing <pkg>`).
