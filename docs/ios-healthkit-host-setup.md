# iOS HealthKit host setup

This repository has no Xcode host project yet. Before enabling the iOS
`HealthImportAction` in a release build, the host application must:

1. Enable the **HealthKit** capability for the application target.
2. Add this `Info.plist` entry:

   ```xml
   <key>NSHealthShareUsageDescription</key>
   <string>Nutri reads your weight, sleep, and activity to prefill an optional daily check-in.</string>
   ```

3. Request read authorization only. Do not request HealthKit write
   authorization and do not add a write usage description.

The shared implementation requests read access for body mass, sleep analysis,
step count, and workout/activity data used for active minutes. It keeps only
the daily aggregate values needed by the check-in and never uploads raw
HealthKit samples. Denied permissions, unavailable HealthKit, and empty
queries are normal empty or partial snapshots.

When a host project is added, apply all three requirements before enabling
`HealthImportAction` in the release build. Verify on an iPhone or
HealthKit-enabled simulator with and without each permission; imported values
must remain editable in the daily check-in form.
