# Death Inventory Backup

Mod Forge dla Minecraft 1.20.1 (DeceasedCraft 5.10.16).

## Funkcje

- Automatyczny zapis ekwipunku przy smierci gracza
- Zapis asynchroniczny na dysk (bez lagow na glownym watku serwera)
- Maksymalnie 20 ostatnich backupow na gracza (starsze sa usuwane)
- Komendy tylko dla operatorow (poziom OP 2)

## Komendy

```
/invbackup list <gracz>
/invbackup restore <gracz> <id>
/invbackup restorelatest <gracz>
/invbackup delete <gracz> <id>
```

## Instalacja

1. Zbuduj mod: `gradlew build`
2. Skopiuj plik `build/libs/deathinventorybackup-1.0.0.jar` do folderu `mods` instancji DeceasedCraft
3. Uruchom serwer lub gre

Backupy zapisywane sa w folderze swiata: `deathinventorybackup/<uuid-gracza>/`

## Wymagania

- Minecraft 1.20.1
- Forge 47.x
- Java 17 (do budowania)
