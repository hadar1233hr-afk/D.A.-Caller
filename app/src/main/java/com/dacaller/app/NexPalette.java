package com.dacaller.app;

/** Local visual tokens inspired by the NEX design. AMOLED stays genuinely black. */
final class NexPalette {
 private NexPalette(){}
 static int background(boolean dark,boolean amoled){return amoled?0xFF000000:dark?0xFF241A17:0xFFF6F0E8;}
 static int surface(boolean dark,boolean amoled){return amoled?0xFF0A0A0A:dark?0xFF342720:0xFFFFFDFC;}
 static int ink(boolean dark){return dark?0xFFF9F1E8:0xFF37291F;}
 static int muted(boolean dark){return dark?0xFFF2E5DD:0xFF4C352A;}
 static int outline(boolean dark){return dark?0xFF634D43:0xFFDCCBBF;}
 static int soft(boolean dark){return dark?0xFF4C3A31:0xFFEFDFD2;}
 static int primary(boolean dark){return dark?0xFFF6A773:0xFFB9542F;}
 static int onPrimary(boolean dark){return dark?0xFF241A17:0xFFFFFFFF;}
 static int call(boolean dark){return dark?0xFF39A98E:0xFF217C69;}
 static int onCall(boolean dark){return dark?0xFF241A17:0xFFFFFFFF;}
}
