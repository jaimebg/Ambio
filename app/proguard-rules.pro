# Project-specific R8 rules.
#
# This file is deliberately almost empty. Hilt, Room, Media3 and Compose each ship their
# own consumer rules inside their AARs, and R8 merges those in automatically — see
# app/build/outputs/mapping/release/configuration.txt for the merged set actually applied.
# Restating them here does not add safety; a blanket `-keep class <lib>.** { *; }` is
# strictly *worse* than the library's own rule, because it opts the whole library out of
# shrinking and obfuscation instead of only the parts that are reached reflectively.
#
# That is not theoretical for this app: `-keep class androidx.compose.** { *; }` plus
# `-keep class androidx.media3.** { *; }` were pinning 23 MB of dex into the release
# bundle. Removing them is the change that took it down to the size it is now.
#
# Before adding a `-keep` here, confirm the library does not already cover the case. If it
# genuinely does not, keep the narrowest thing that works — a class or a member, never a
# package wildcard — and write down what reaches it reflectively.

# Kotlin generic signatures and annotations, which the AGP default file does not fully
# cover. These are attribute retentions, not keeps: they do not hold any class alive.
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
