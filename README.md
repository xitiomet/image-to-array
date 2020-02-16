## Image Array Tool ##
![](https://raw.githubusercontent.com/xitiomet/image-to-array/master/res/icon128.png)
command line tool for converting images to data arrays

```
usage: ita
 -2,--output-2d-array <arg>   Output a RGB two dimensional C++ style
                              struct array
 -?,--help                    Shows help
 -a,--output-ascii            Output an ascii art image
 -c,--output-array <arg>      Output a RGB C++ style struct array
 -d,--debug                   Turn on debug.
 -i,--input <arg>             Input image file
 -s,--scale <arg>             Scale image (ex: 320x240 or 0.5)
```

Example Usage
```
$ ita -i hearts.png -c CRGB
CRGB hearts[80] = { CRGB(102, 102, 102), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(0, 0, 0), CRGB(0, 0, 0), CRGB(0, 0, 0), CRGB(0, 0, 0), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(93, 93, 93), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(0, 0, 0), CRGB(0, 0, 0), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(96, 96, 96), CRGB(96, 96, 96), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(0, 0, 0), CRGB(131, 24, 24), CRGB(254, 93, 225), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(0, 0, 0), CRGB(113, 113, 113), CRGB(113, 113, 113), CRGB(0, 0, 0), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(0, 0, 0), CRGB(93, 93, 93), CRGB(0, 0, 0), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(0, 0, 0), CRGB(104, 104, 104), CRGB(123, 123, 123), CRGB(123, 123, 123), CRGB(104, 104, 104), CRGB(0, 0, 0), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(0, 0, 0), CRGB(89, 89, 89) };
```