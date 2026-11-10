# My Budget

A personal finance app for Android that tracks income and spending, explains where your money goes, and helps you save for the things you want. Everything is stored on the device: no accounts, no ads, no tracking.

> **Status:** personal project, in daily use. Not published to any app store.

<!-- Add screenshots here
<p align="center">
  <img src="docs/screenshots/home.png" width="230" />
  <img src="docs/screenshots/insights.png" width="230" />
  <img src="docs/screenshots/goals.png" width="230" />
</p>
-->

## Why I built it

Most budgeting apps either lock their best features behind a subscription or want access to your bank account. I wanted one that shows **how much I can still spend today**, how this month compares with earlier ones, and what I should change, while keeping my financial data on my own phone.

## Features

**Home**
- Monthly hero card: amount left to spend, progress bar, and a daily allowance for the days remaining
- Income, spending and savings at a glance, with savings rate
- Daily spending chart with the daily budget shown as a dashed line
- Top categories with optional per-category limits
- Upcoming bills and recent transactions
- Month navigation, so every previous month is one tap away

**Activity**
- Every transaction grouped by day, with search and Expense / Income filters
- Add, edit and delete expenses and income, with category, note and date

**Insights**
- *Monthly:* category donut chart, comparison with the previous month, biggest expenses
- *Yearly:* income vs spending for all 12 months, savings rate, average monthly spend, costliest month, trend detection, top categories, and a month-by-month table
- **Written advice** generated from your data, for example: over or ahead of budget, projected overspend at the current pace, categories that jumped versus last month, low savings rate, and small purchases that add up

**Savings goals**
- Goals such as a trip or a new gadget, with target, amount saved and optional deadline
- Progress ring and the monthly amount needed to finish on time
- Add or withdraw money at any point

**Bills & subscriptions**
- Recurring bills with a due day each month
- One-tap "Mark as paid" logs the expense automatically
- Reminders the day before and on the due date, restored after a reboot

**Data & settings**
- Backup and restore as a JSON file, and export every transaction as CSV
- Light, dark or system theme, and an optional daily "log your spending" reminder

## Tech

- **Kotlin** and **Jetpack Compose** (Material 3), with no third-party libraries
- **Custom charts** (bar, donut, grouped yearly bars, progress rings) drawn with Compose `Canvas`
- **Rule-based insights engine** that turns monthly and yearly figures into plain-language advice
- Local JSON persistence in app-private storage
- **AlarmManager**, `BroadcastReceiver` and notifications for bill and daily reminders, plus a boot receiver to restore them
- **Storage Access Framework** for backup, restore and CSV export
- Indian number formatting (₹ with lakh and crore grouping)
- Adaptive vector launcher icon
- **GitHub Actions** builds a ready-to-install APK on every push

## Project structure

```
app/src/main/
  java/com/mydash/planner/
    MainActivity.kt     Data model, storage, advice engine, theme, charts, all screens and dialogs
    Extras.kt           Reminder scheduling, notification and boot receivers
  res/                  Adaptive launcher icon
  AndroidManifest.xml
.github/workflows/build.yml   APK build
```

## Build it yourself

**Android Studio:** open the project and run it on a device or emulator.

**GitHub Actions:** push to GitHub, open the **Actions** tab, and download the `planner-apk` artifact from the latest run. Allow installs from unknown sources on your phone to install it.

## What I learned

- Designing a fintech-style UI system (colour palette, cards, hero summary) in Compose
- Building charts from scratch with `Canvas` instead of a library
- Modelling money data across months and years, and deriving insights from it
- Scheduling reliable background reminders on modern Android, including notification permissions
- Automating builds with CI

## Roadmap

- [ ] Optional cloud sync with sign-in
- [ ] Recurring income and recurring transactions
- [ ] Unit tests for the insights engine and date logic
- [ ] Home-screen widget showing what's left to spend

## Author

Built by **[Md Shamoel Farooqui]**. [LinkedIn](https://www.linkedin.com/in/md-shamoel/) · [Email](farooquishamoel@gmail.com)
