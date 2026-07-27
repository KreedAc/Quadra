package it.quadra.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.LocalActivity
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Dalla chiave dell'icona al disegno.
 *
 * Il modulo :core non conosce R.drawable né gli ImageVector di Compose: salva una
 * stringa e basta. La traduzione sta qui, perché è l'unico punto che deve cambiare se un
 * domani si passa a un set di icone disegnate su misura.
 */
fun iconFor(key: String?): ImageVector = when (key) {
    "cart" -> Icons.Rounded.ShoppingCart
    "coffee" -> Icons.Rounded.LocalCafe
    "restaurant" -> Icons.Rounded.Restaurant
    "car" -> Icons.Rounded.DirectionsCar
    "fuel" -> Icons.Rounded.LocalGasStation
    "building" -> Icons.Rounded.Apartment
    "bolt" -> Icons.Rounded.Bolt
    "pill" -> Icons.Rounded.MedicalServices
    "bag" -> Icons.Rounded.Checkroom
    "ticket" -> Icons.Rounded.LocalActivity
    "receipt" -> Icons.Rounded.Receipt
    "pets" -> Icons.Rounded.Pets
    "child" -> Icons.Rounded.ChildCare
    "gift" -> Icons.Rounded.Redeem
    "wallet" -> Icons.Rounded.AccountBalanceWallet
    "credit_card" -> Icons.Rounded.CreditCard
    "bank" -> Icons.Rounded.AccountBalance
    "savings" -> Icons.Rounded.Savings
    "swap" -> Icons.Rounded.SwapHoriz
    else -> Icons.Rounded.MoreHoriz
}
