# UI Coordinates

Device: 37281JEHN03065
Resolution: 1080x2400

## Welcome Screen

| Element | Bounds | Center (tap) |
|---------|--------|--------------|
| Create Account button | [84,1176][996,1302] | (540, 1239) |
| Import Existing Account button | [84,1344][996,1470] | (540, 1407) |

## Create Account Screen

| Element | Bounds | Center (tap) |
|---------|--------|--------------|
| Display Name field | [84,1053][996,1221] | (540, 1137) |
| Create Account button | [84,1306][996,1432] | (540, 1369) |

## Main Screen - Bottom Navigation

| Element | Bounds | Center (tap) |
|---------|--------|--------------|
| Chats tab | [0,2127][346,2337] | (173, 2232) |
| Contacts tab | [367,2127][713,2337] | (540, 2232) |
| Settings tab | [734,2127][1080,2337] | (907, 2232) |
| FAB (New conversation) | [891,1938][1038,2085] | (965, 2012) |

## Chats Screen - List Items

| Element | Bounds | Center (tap) |
|---------|--------|--------------|
| First chat item | [0,404][1080,614] | (540, 509) |
| Second chat item | [0,614][1080,824] | (540, 719) |
| Third chat item | [0,824][1080,1034] | (540, 929) |

## Notes

- Use `adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml` to get exact bounds
- Coordinates may vary slightly based on system UI changes
