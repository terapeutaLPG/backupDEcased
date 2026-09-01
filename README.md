# Death Inventory Backup

Mod zapisujacy ekwipunek i XP gracza przy smierci. Autor: **jaruso99**

## Dwie wersje (rozne loadery!)

| Paczka | Minecraft | Loader | Plik JAR |
|--------|-----------|--------|----------|
| **DeceasedCraft** | 1.20.1 | Forge | `build/libs/deathinventorybackup-1.2.0.jar` |
| **All The Mods 10** | 1.21.1 | NeoForge | `neoforge/build/libs/deathinventorybackup-neoforge-1.2.0.jar` |

**Nie mieszaj wersji** - ATM10 wymaga NeoForge 1.21.1, DeceasedCraft wymaga Forge 1.20.1.

## Funkcje

- Automatyczny zapis ekwipunku i XP przy smierci
- Zapis asynchroniczny na dysk
- Maksymalnie 20 backupow na gracza
- Komendy tylko dla operatorow (OP 2)
- GUI podgladu ekwipunku
- Powiadomienie po smierci z numerem backupu i ID

## Komendy

```
/invbackup list <gracz>
/invbackup gui <gracz> <nr>
/invbackup restore <gracz> <nr>
/invbackup restorelatest <gracz>
/invbackup delete <gracz> <nr>
```

## Budowanie

DeceasedCraft (Forge 1.20.1):
```
gradlew build
```

All The Mods 10 (NeoForge 1.21.1):
```
cd neoforge
gradlew build
```

Backupy zapisywane sa w folderze swiata: `deathinventorybackup/<uuid-gracza>/`
