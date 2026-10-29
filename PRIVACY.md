# Ownkey Keyboard privacy policy

Last updated: 29 October 2026. This policy covers Ownkey Keyboard for Android, version 0.11.0 and later.

Ownkey Keyboard ("Ownkey") is an open-source Android keyboard with voice dictation and AI rewrite. This policy explains what Ownkey does with your data, what leaves your phone and who receives it.

## Short version

- Ownkey has no account and no server. It sends nothing to us.
- Typing, suggestions, autocorrect and learning run on your phone.
- Your voice or text only leaves the phone when you use cloud dictation or AI rewrite. It then goes straight to the provider you set up, under your own API key.
- With Orukeet, dictation runs on the phone and the audio stays there.
- Ownkey has no ads, no analytics and no automatic crash reporting.

## Who is responsible

Ownkey is made by Bart van der Meeren, trading as bvdm.ai (sole proprietorship, Dutch Chamber of Commerce number 99381125), Troelstrastraat 71, 4191 HT Geldermalsen, the Netherlands. He is the data controller for Ownkey.

Privacy questions: ownkey@bvdm.ai

## What stays on your phone

A keyboard sees what you type. Ownkey needs that to work, and keeps the following on your phone only:

- **Typing.** Suggestions, autocorrect, next-word predictions and spell checking run on the phone.
- **Learned data.** Ownkey learns short word sequences and the email addresses you type, to improve predictions. Learning is off in incognito mode, in password fields and when you turn off personalized suggestions. You can clear learned data under Settings > Privacy & backup.
- **Your dictionaries.** This covers words you add for typing, plus the personal dictionary for dictation: its words, corrections and filler words.
- **Clipboard history.** It is on by default and you can turn it off in the clipboard settings.
- **Settings and layouts.** Settings, themes, keyboard layouts and emoji history.
- **API keys.** They are stored encrypted, with a key held in Android Keystore.
- **The Orukeet model,** if you download it.
- **Crash details.** When Ownkey crashes, it saves the error on the phone and shows it to you. Nothing is sent automatically. You can copy the report and choose to post it in a GitHub issue.

Recordings are temporary. Ownkey deletes the audio file once transcription finishes or you cancel. Any leftover recordings are removed the next time the app starts.

To help with setup, Ownkey asks Android which keyboards and spell checkers are installed. That check stays on the phone.

## What leaves your phone

Ownkey only sends data when you use a feature that needs it. Dictation records only after you tap the microphone. Rewrite only sends text after you pick a rewrite. Both are switched off in password fields and in incognito mode.

| What you do | What is sent | Where it goes |
| --- | --- | --- |
| Type | Nothing | |
| Dictate with Orukeet | Nothing | |
| Dictate with a cloud provider | The recording, the model name and the language code, unless you chose Auto. By default, with Mistral and OpenAI, the words in your personal dictionary also go along as recognition hints. You can turn hints off, or choose to send them to another endpoint. | The dictation endpoint you set. The default is Mistral (api.mistral.ai). |
| Rewrite | The text you selected, or the whole text field if nothing is selected. Also the instruction (a preset, a style you wrote, or your spoken instruction) and the model name. | The rewrite endpoint you set. The default is OpenRouter (openrouter.ai). There are presets for OpenAI, Anthropic and Mistral, and you can enter any compatible endpoint. |
| Rewrite by voice | First your spoken instruction, as with dictation. Then the selected text and the recognized instruction. | Your dictation provider, or Orukeet on the phone. Then your rewrite provider. |
| Use System voice input | Ownkey switches to your phone's voice typing app and records nothing itself. | That app, under its own privacy policy |
| Download Orukeet | A download request, with no content from your phone | GitHub, which hosts the model files |

Each request to a provider carries your API key for that provider. Like any internet connection, it also reveals your IP address. Cloud requests include Android's standard user agent, which names your Android version and phone model. Requests to OpenRouter also name the app as "Ownkey Keyboard".

## The providers you choose

Cloud requests go under your own account with the provider you set up, and we never receive them. From the moment a request reaches the provider, its privacy policy, retention terms and billing apply. Some providers process data outside the European Union. The presets point to:

- Mistral AI: https://legal.mistral.ai/terms/privacy-policy
- OpenRouter: https://openrouter.ai/privacy
- OpenAI: https://openai.com/policies/privacy-policy/
- Anthropic: https://www.anthropic.com/legal/privacy

If you enter your own endpoint, whoever runs that server receives the data.

## Other parties

- **GitHub** hosts the Orukeet model files, Ownkey's source code and the issue tracker. GitHub's privacy statement applies when you download the model or open a GitHub link: https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement
- **Google Play** distributes Ownkey. Google handles your Play account and installs under its privacy policy: https://policies.google.com/privacy. Google gives us statistics such as install counts. It may also give us crash and performance reports from phones whose owners let Android share diagnostics with Google.
- **Android backup.** Ownkey allows Android's own backup. It can copy Ownkey's settings, its keyboard and theme files, and your typing dictionary to your Google account. API keys, learned data, the personal dictionary for dictation and clipboard history are left out. Google runs this backup, and we can't access it.
- **Backup files you make.** Under Settings > Privacy & backup you can create a backup file, then save it or share it wherever you choose. It contains your settings, keyboard and theme files and personal dictionary. Clipboard items are included only if you tick them. API keys are never included.

## What we don't do

- No ads, no analytics, no tracking libraries and no automatic crash reporting.
- We never receive your typing, recordings or rewrite text, so we have nothing to sell or share.

## Permissions

- **Microphone:** records only while you dictate or speak a rewrite instruction.
- **Internet and network state:** used for cloud dictation, rewrite and the Orukeet download.
- **Notifications:** show download progress and crash alerts.
- **Foreground service (data sync) and user-initiated jobs:** finish the Orukeet download you started, with a progress notification you can cancel. Android's WorkManager library adds wake-lock and start-at-boot permissions for the same download.
- **Vibration:** key-press feedback.

Ownkey doesn't ask for your contacts, location, photos or files.

## Security

- All connections use HTTPS. Android blocks unencrypted connections for Ownkey.
- API keys are encrypted with AES-256 under a key in Android Keystore. They never go into backup files.
- Before Orukeet is used, each model file is checked against a SHA-256 checksum built into the app.
- Dictation and rewrite are off in password fields and in incognito mode.
- The source code is public: https://github.com/MajesteitBart/ownkey-keyboard

## How long data is kept

- **On our side:** nothing. Ownkey has no server.
- **On your phone:** until you delete it, clear Ownkey's storage or uninstall the app. Recordings are deleted right after use.
- **At your provider:** as set by that provider's terms.
- **Emails you send us:** kept only as long as we need to answer you.
- **GitHub issues:** public on GitHub until you or we delete them.

## Deleting your data

- **Learned data:** Settings > Privacy & backup.
- **API keys:** "Remove API key" under Settings > AI.
- **The Orukeet model:** "Delete model" under Settings > AI.
- **Everything:** clear Ownkey's storage in Android's app settings, or uninstall Ownkey.
- **Data at a provider:** use that provider's account settings or contact the provider.

## Children

Ownkey is not made for children. Its AI features need an API key from a provider, and providers set their own minimum age. Ownkey sends no data to us, from children or anyone else.

## Your rights under the GDPR

You have the right to access, correct or delete your personal data, to restrict or object to its use, and to receive it in a portable format. Since Ownkey sends nothing to us, we usually hold no personal data about you. If you email us, we keep that email in order to answer you. The legal basis is our legitimate interest in replying (Article 6(1)(f) GDPR). For data held by a provider you chose, contact that provider. You can complain to the Dutch Data Protection Authority (Autoriteit Persoonsgegevens, https://autoriteitpersoonsgegevens.nl) or to the authority where you live.

## Changes

We update this policy when Ownkey's handling of data changes. This file is part of Ownkey's public repository, and its history shows every earlier version. If a change affects what leaves your phone, the release notes mention it too.

## Contact

Email ownkey@bvdm.ai, or write to the address under "Who is responsible".
