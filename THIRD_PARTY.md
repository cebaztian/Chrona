# Third-party assets

Assets, not code dependencies. They are bundled in the APK and need no network. The license texts are
in `app/src/main/assets/licenses/`.

| Asset | Source (pinned) | License |
|---|---|---|
| Newsreader (variable, `res/font/newsreader.ttf`) | https://github.com/google/fonts/tree/7af0d90b4d532ffa4b041937b560c6df4e7b1e95/ofl/newsreader (`Newsreader[opsz,wght].ttf`) | SIL OFL 1.1 (`OFL-Newsreader.txt`) |
| Plus Jakarta Sans (variable, `res/font/plus_jakarta_sans.ttf`) | https://github.com/google/fonts/tree/7af0d90b4d532ffa4b041937b560c6df4e7b1e95/ofl/plusjakartasans (`PlusJakartaSans[wght].ttf`) | SIL OFL 1.1 (`OFL-PlusJakartaSans.txt`) |
| Phosphor icons (`res/drawable/ic_*.xml`) | https://github.com/phosphor-icons/core/tree/2b75f3ad12b420c9504ef05df8d2564a28f8500e (`assets/light` and `assets/regular`) | MIT (`MIT-Phosphor.txt`) |

The Phosphor drawables are generated, not hand-edited:

```bash
python tools/phosphor_to_vector.py <phosphor-core checkout> app/src/main/res/drawable "light:<names>" "regular:<names>"
```

Light weight is the default (`ic_<name>.xml`); the Regular weight, used on the ringing and mission
screens, gets the suffix `_regular`. The script fails on any element that is not a filled `<path>`.
