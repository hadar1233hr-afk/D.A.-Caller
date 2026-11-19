Read-only quantization audit for CI44447145702

Pinned commit: e424b61ebd9ecc1adb2a23d2ee4f18c6a8edd624.

- InputDispatcher.cpp:4491-4512 explicitly maps injection from logical display to input display/server space with display transform inverse, preserving touchscreen translation.
- InputDispatcher.cpp:2070-2083 casts transformed pointer XY to int32_t before target lookup. C++ conversion truncates toward zero, matching Java (int), including negative fractions.
- libs/input/Input.cpp:66-78 and 875-887/920-930 show TOUCHSCREEN receives the complete affine transform.
- libs/ui/Transform.cpp:229-234 and 373-417 show float inverse coefficient and multiply/add evaluation; algebraic double division has the same geometry but does not promise bit equality.
- libs/ui/Transform.cpp:527-529 formats coefficients with %.4f. The raw dump therefore cannot encode every exact floating-point edge.
- libs/ui/Region.cpp:251-259 and libs/gui/WindowInfo.cpp:64-66 preserve inclusive left/top, exclusive right/bottom.

For the printed CI matrix, all 480 integer logical X values and all 854 integer logical Y values produce identical cast-to-int server coordinates under a float-operation replay versus double division. This is offline arithmetic evidence, not an actual native run. Actual center (240,90) maps to server (160,94), well inside island [30,67][290,123]. Logical y36 remains StatusBar-owned and y37 is excluded; rounding transformed StatusBar bottom 36.0223 outward to37 is conservative.

No repository code edits, Gradle, APK publication, CI changes or gesture injection occurred.
