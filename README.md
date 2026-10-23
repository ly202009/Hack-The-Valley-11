## Kaze Mesh - Project Kaze 3rd Place
Our team made created a real-time safety network that keeps you tethered to your friends when you're out. Our app allows you to create private groups where you can add all your friends and keep track of each other's locations and safety.

Check out our project on [Devpost.](https://devpost.com/software/squad-mesh)

<p align="center">
  <img width="500" height="auto" alt="Friend Safety Escalation State Machine-2026-10-18-095734" src="https://github.com/user-attachments/assets/a073e934-67ad-4f60-aef5-7b38a63cb7f2" />
</p>

## Table of Contents

1. [Features](#features)

2. [Tech Stack](#tech-stack)

3. [Running Locally](#running-project-files)

## Features
- Solo Mode - If you're out on your own, you can still use Kaze Mesh to alert an emergency contact by discreetly alternating between the volume up volume down buttons 3 times.
- Group Proximity - If someone wanders outside of a group-set proximity for a certain period of time, the app will send them a checkup. If they fail to respond after some time or input a decoy pin, the group is notified.
- Routine Check - Checkup sent to all users in a group at adjustable intervals.
- Nudge - A user can nudge another group member to send them an instant checkup to see if they're okay.
- Emergency Contact SOS - If a user goes into danger mode, either by inputting a decoy pin or using their silent trigger, an SMS text message with a google map link to their coordinates will be sent to their list of emergency contacts.
- Silent Trigger - Press volume up and down 3 times rapidly to instantly let your group know you are in danger. Group members can disarm danger status by inputting their real pins. This feature works even when the app is closed.
- Checkups - Each checkup prompts the user being checked to input a pin. If they input their real pin, they are marked safe for everyone. If they input their decoy pin, the app will show that they are safe while all other users in their group are notified that they are in danger and their last known location is sent to their emergency contacts.

<p align="center">
  <img width="200" alt="Screenshot 2026-10-18 062536" src="https://github.com/user-attachments/assets/e5508afd-de9f-4e8c-a438-fdf782d756dc" />
  <img width="200" alt="image" src="https://github.com/user-attachments/assets/52844ae9-c323-42d5-8efc-36ed893820ec" />
  <img width="200" alt="image" src="https://github.com/user-attachments/assets/11b2bf14-fb2e-4bc1-be76-f2cbb4aa98ac" />
</p>

## Tech Stack
- Developed in Android Studio Rabbit 1 with a Kotlin project.
- Jetpack Compose for UI.
- Backend relies on Firebase real-time database for live updates between all connected devices.
- Maps for displaying the location of users relies on MapLibre API for map data, 3D buildings, icons, and user interface.

## Running Project Files
1. Download Android Studio Rabbit 1 (Best to match version).
2. Fork our github repository.
3. Clone to local PC (```git clone https://github.com/ly202009/Hack-The-Valley-11```)
4. Set up Firebase project with an android app and anonymous authentication enabled
   - To match our provided files, name the package ```com.example.htv11```
   - Follow the instructions on the website for adding the config file to the project and adding Firebase SDK
   - Also make sure to set the real-time database rules on Firebase as follows, otherwise, users cannot read or write to the database:
  ```JSON
{
  "rules": {
    ".read": true,
    "UUID": {
      "$uid": {
        ".write": "auth != null"
      }
    },
    "locations": {
      "$uid": {
        ".write": "auth != null && auth.uid === $uid"
      }
    },
    "groups": {
      "$uid": {
        ".write": "auth != null"
      }
    },
    "users": {
      "$uid": {
        ".write": "auth != null && auth.uid === $uid",
        "status": {
          ".write": "auth != null"
        }
      }
    }
  }
}
```
5. Add or locate the local.properties file to project root.
6. Within local.properties:
   - Set the following variable equal to a MapLibre API key:
     ```MAPTILER_API_KEY=yourkeyhere```
   - After compiling, Android Studio gradle should handle downloading all dependencies, including MapLibre, Jetpack Compose, etc.
7. Start an android virtual machine or connect an android device via USB and run a build of the app. If you downloaded to a real android device, the location data and settings will be real if you turn it on. On emulators, you can manually set the location.
