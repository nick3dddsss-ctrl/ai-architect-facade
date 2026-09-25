#!/usr/bin/env bash
set -euxo pipefail
D=/tmp/makeup231/app/src/main/res/drawable-nodpi
mkdir -p "$D"
UA='MakeupTV/2.3.1 (Android TV build)'
get(){ curl -L --fail --retry 3 -A "$UA" "$1" -o "$2"; }
get 'https://commons.wikimedia.org/wiki/Special:Redirect/file/Beautiful_face_girl.jpg?width=960' "$D/hero_glam.jpg"
get 'https://commons.wikimedia.org/wiki/Special:Redirect/file/Beautiful_face_girl.jpg?width=640' "$D/cover_makeup_start.jpg"
get 'https://commons.wikimedia.org/wiki/Special:Redirect/file/A_woman_wearing_Eye_Shadow.jpg?width=640' "$D/cover_day.jpg"
get 'https://commons.wikimedia.org/wiki/Special:Redirect/file/Bold_Eye_Makeup_(Unsplash).jpg?width=640' "$D/cover_evening.jpg"
cp "$D/cover_day.jpg" "$D/cover_bride.jpg"
cp "$D/cover_makeup_start.jpg" "$D/cover_age.jpg"
get 'https://commons.wikimedia.org/wiki/Special:Redirect/file/Makeup_brushes_and_eyeshadows_(Unsplash).jpg?width=640' "$D/cover_products.jpg"
get 'https://commons.wikimedia.org/wiki/Special:Redirect/file/Makeup_brushes_(35675834556).jpg?width=640' "$D/cover_brushes.jpg"
cp "$D/cover_makeup_start.jpg" "$D/cover_tone.jpg"
cp "$D/cover_day.jpg" "$D/cover_eyes.jpg"
get 'https://commons.wikimedia.org/wiki/Special:Redirect/file/Makeup-beauty-lipstick-make-up_(24030573540).jpg?width=640' "$D/cover_lips.jpg"
cp "$D/cover_evening.jpg" "$D/cover_smokey.jpg"
cp "$D/cover_day.jpg" "$D/cover_arrows.jpg"
file "$D"/*.jpg
