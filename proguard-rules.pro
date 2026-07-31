-keep public class juloo.cdict.* {
  public protected private *;
}

-keep public class juloo.keyboard2.autocorrect.Hunspell {
  public protected private *;
}

# pdfbox-android supports JPEG 2000 only when its optional decoder is present.
-dontwarn com.gemalto.jp2.JP2Decoder
