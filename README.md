## Image To Array Tool ##
![](https://raw.githubusercontent.com/xitiomet/image-to-array/master/src/main/resources/icon128.png)

Hosted on github https://github.com/xitiomet/image-to-array

Simple Command line tool for converting images to data arrays. I made this to use with my RGB Shades (http://macetech.com/store/index.php?main_page=product_info&products_id=59), I wanted a simple way to convert any image into an array of pixels compatable with Arduino's FastLED Library. I may have gone past the original scope a bit, i added scaling and palette manipulation.

```bash
usage: ita
 -2,--output-2d-array <arg>   Output a RGB two dimensional C++ struct
                              array
 -6,--output-base64           Output a base64 png string
 -?,--help                    Shows help
 -a,--output-ascii            Output a 24-bit ASCII art image
 -c,--output-array <arg>      Output a RGB C++ struct array
 -d,--details                 Output image details
 -i,--input <arg>             Input image file or URL
 -o,--output <arg>            Output file (.txt or .png)
 -p,--input-palette <arg>     Input image file for color palette filter
 -r,--row-numbers             Include row numbers on ASCII art
 -s,--scale <arg>             Scale image (ex: 320x240 or 0.5)
```

### Example Usage ###
hearts.png (16x5 Image i want displayed on glasses)

![](https://raw.githubusercontent.com/xitiomet/image-to-array/master/src/main/resources/hearts2.png)
```bash
$ ita -i hearts.png -c CRGB
CRGB hearts[80] = { CRGB(102, 102, 102), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(0, 0, 0), CRGB(0, 0, 0), CRGB(0, 0, 0), CRGB(0, 0, 0), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(93, 93, 93), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(0, 0, 0), CRGB(0, 0, 0), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(96, 96, 96), CRGB(96, 96, 96), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(0, 0, 0), CRGB(131, 24, 24), CRGB(254, 93, 225), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(0, 0, 0), CRGB(113, 113, 113), CRGB(113, 113, 113), CRGB(0, 0, 0), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(0, 0, 0), CRGB(93, 93, 93), CRGB(0, 0, 0), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(0, 0, 0), CRGB(104, 104, 104), CRGB(123, 123, 123), CRGB(123, 123, 123), CRGB(104, 104, 104), CRGB(0, 0, 0), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(0, 0, 0), CRGB(89, 89, 89) };
```
if you want to use a struct other then CRGB any value can be passed to -c for instance "-c MyStruct" would produce

```bash
$ ita -i hearts.png -c MyStruct
MyStruct hearts[80] = { MyStruct(102, 102, 102), MyStruct(131, 24, 24), MyStruct(255, 97, 239), MyStruct(131, 24, 24), MyStruct(255, 97, 239), MyStruct(131, 24, 24), MyStruct(0, 0, 0), MyStruct(0, 0, 0), MyStruct(0, 0, 0), MyStruct(0, 0, 0), MyStruct(131, 24, 24), MyStruct(255, 97, 239), MyStruct(131, 24, 24), MyStruct(255, 97, 239), MyStruct(131, 24, 24), MyStruct(93, 93, 93), MyStruct(131, 24, 24), MyStruct(255, 97, 239), MyStruct(255, 97, 239), MyStruct(255, 97, 239), MyStruct(255, 97, 239), MyStruct(255, 97, 239), MyStruct(131, 24, 24), MyStruct(0, 0, 0), MyStruct(0, 0, 0), MyStruct(131, 24, 24), MyStruct(255, 97, 239), MyStruct(255, 97, 239), MyStruct(255, 97, 239), MyStruct(255, 97, 239), MyStruct(255, 97, 239), MyStruct(131, 24, 24), MyStruct(131, 24, 24), MyStruct(255, 97, 239), MyStruct(255, 97, 239), MyStruct(255, 97, 239), MyStruct(255, 97, 239), MyStruct(255, 97, 239), MyStruct(131, 24, 24), MyStruct(96, 96, 96), MyStruct(96, 96, 96), MyStruct(131, 24, 24), MyStruct(255, 97, 239), MyStruct(255, 97, 239), MyStruct(255, 97, 239), MyStruct(255, 97, 239), MyStruct(255, 97, 239), MyStruct(131, 24, 24), MyStruct(0, 0, 0), MyStruct(131, 24, 24), MyStruct(254, 93, 225), MyStruct(255, 97, 239), MyStruct(255, 97, 239), MyStruct(131, 24, 24), MyStruct(0, 0, 0), MyStruct(113, 113, 113), MyStruct(113, 113, 113), MyStruct(0, 0, 0), MyStruct(131, 24, 24), MyStruct(255, 97, 239), MyStruct(255, 97, 239), MyStruct(255, 97, 239), MyStruct(131, 24, 24), MyStruct(0, 0, 0), MyStruct(93, 93, 93), MyStruct(0, 0, 0), MyStruct(131, 24, 24), MyStruct(255, 97, 239), MyStruct(131, 24, 24), MyStruct(0, 0, 0), MyStruct(104, 104, 104), MyStruct(123, 123, 123), MyStruct(123, 123, 123), MyStruct(104, 104, 104), MyStruct(0, 0, 0), MyStruct(131, 24, 24), MyStruct(255, 97, 239), MyStruct(131, 24, 24), MyStruct(0, 0, 0), MyStruct(89, 89, 89) };
```

Once you have the array to your image here is how its used to create an effect for the RGB Shades:
```c
void hearts()
{
  if (effectInit == false) {
    effectInit = true;
    effectDelay = 500;
  }

  //------Generated by image-to-array--------
  CRGB hearts[80] = { CRGB(102, 102, 102), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(0, 0, 0), CRGB(0, 0, 0), CRGB(0, 0, 0), CRGB(0, 0, 0), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(93, 93, 93), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(0, 0, 0), CRGB(0, 0, 0), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(96, 96, 96), CRGB(96, 96, 96), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(0, 0, 0), CRGB(131, 24, 24), CRGB(254, 93, 225), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(0, 0, 0), CRGB(113, 113, 113), CRGB(113, 113, 113), CRGB(0, 0, 0), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(0, 0, 0), CRGB(93, 93, 93), CRGB(0, 0, 0), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(0, 0, 0), CRGB(104, 104, 104), CRGB(123, 123, 123), CRGB(123, 123, 123), CRGB(104, 104, 104), CRGB(0, 0, 0), CRGB(131, 24, 24), CRGB(255, 97, 239), CRGB(131, 24, 24), CRGB(0, 0, 0), CRGB(89, 89, 89) };
  //-----------------------------------------
  
  uint8_t i = 0;
  for (byte y = 0; y < kMatrixHeight; y++)
  {
    for (byte x = 0; x < kMatrixWidth; x++)
    {
      uint8_t xy = XY(x, y);
      leds[xy] = hearts[i++];
    }
  }
}

```
