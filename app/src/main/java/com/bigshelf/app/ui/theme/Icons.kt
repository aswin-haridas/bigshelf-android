package com.bigshelf.app.ui.theme

import androidx.compose.ui.graphics.vector.ImageVector
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.bold.Check as BoldCheck
import com.adamglin.phosphoricons.bold.X as BoldX
import com.adamglin.phosphoricons.fill.CheckCircle as FillCheckCircle
import com.adamglin.phosphoricons.fill.FlowerTulip as FillFlowerTulip
import com.adamglin.phosphoricons.fill.Heart as FillHeart
import com.adamglin.phosphoricons.fill.Star as FillStar
import com.adamglin.phosphoricons.fill.XCircle as FillXCircle
import com.adamglin.phosphoricons.fill.Images as FillImages
import com.adamglin.phosphoricons.fill.SquaresFour as FillSquaresFour
import com.adamglin.phosphoricons.fill.Users as FillUsers
import com.adamglin.phosphoricons.fill.Info as FillInfo
import com.adamglin.phosphoricons.fill.Play as FillPlay
import com.adamglin.phosphoricons.fill.WarningCircle as FillWarningCircle
import com.adamglin.phosphoricons.regular.Archive
import com.adamglin.phosphoricons.regular.Broom
import com.adamglin.phosphoricons.regular.Star
import com.adamglin.phosphoricons.regular.SelectionAll
import com.adamglin.phosphoricons.regular.Prohibit
import com.adamglin.phosphoricons.regular.GearSix
import com.adamglin.phosphoricons.regular.Funnel
import com.adamglin.phosphoricons.regular.FlowArrow
import com.adamglin.phosphoricons.regular.Copy
import com.adamglin.phosphoricons.regular.Clock
import com.adamglin.phosphoricons.regular.ArrowUpRight
import com.adamglin.phosphoricons.regular.ArrowUUpLeft
import com.adamglin.phosphoricons.regular.ArrowClockwise
import com.adamglin.phosphoricons.regular.ArrowCounterClockwise
import com.adamglin.phosphoricons.regular.ArrowLeft
import com.adamglin.phosphoricons.regular.ArrowsClockwise
import com.adamglin.phosphoricons.regular.CaretRight
import com.adamglin.phosphoricons.regular.DownloadSimple
import com.adamglin.phosphoricons.regular.Eye
import com.adamglin.phosphoricons.regular.EyeSlash
import com.adamglin.phosphoricons.regular.FlowerTulip
import com.adamglin.phosphoricons.regular.Folder
import com.adamglin.phosphoricons.regular.Gear
import com.adamglin.phosphoricons.regular.GitMerge
import com.adamglin.phosphoricons.regular.Grains
import com.adamglin.phosphoricons.regular.Heart
import com.adamglin.phosphoricons.regular.Images
import com.adamglin.phosphoricons.regular.Info
import com.adamglin.phosphoricons.regular.List
import com.adamglin.phosphoricons.regular.MagnifyingGlass
import com.adamglin.phosphoricons.regular.PencilSimple
import com.adamglin.phosphoricons.regular.SignOut
import com.adamglin.phosphoricons.regular.Spinner
import com.adamglin.phosphoricons.regular.SquaresFour
import com.adamglin.phosphoricons.regular.Stack
import com.adamglin.phosphoricons.regular.Tag
import com.adamglin.phosphoricons.regular.Trash
import com.adamglin.phosphoricons.regular.UploadSimple
import com.adamglin.phosphoricons.regular.Users

/**
 * Phosphor icons - the same set the web app imports from @phosphor-icons/react.
 * Names keep the web's `XxxIcon` naming so screens read like their .tsx counterparts.
 * Sizes stay on the web scale: 12 / 14 / 16 / 20 / 24 / 32 dp.
 */
object BsIcons {
    val Archive: ImageVector get() = PhosphorIcons.Regular.Archive
    val ArrowUUpLeft: ImageVector get() = PhosphorIcons.Regular.ArrowUUpLeft
    val ArrowUpRight: ImageVector get() = PhosphorIcons.Regular.ArrowUpRight
    val Clock: ImageVector get() = PhosphorIcons.Regular.Clock
    val Copy: ImageVector get() = PhosphorIcons.Regular.Copy
    val FlowArrow: ImageVector get() = PhosphorIcons.Regular.FlowArrow
    val Funnel: ImageVector get() = PhosphorIcons.Regular.Funnel
    val GearSix: ImageVector get() = PhosphorIcons.Regular.GearSix
    val Prohibit: ImageVector get() = PhosphorIcons.Regular.Prohibit
    val SelectionAll: ImageVector get() = PhosphorIcons.Regular.SelectionAll
    val Star: ImageVector get() = PhosphorIcons.Regular.Star
    val Broom: ImageVector get() = PhosphorIcons.Regular.Broom
    val XCircleFill: ImageVector get() = PhosphorIcons.Fill.FillXCircle
    val StarFill: ImageVector get() = PhosphorIcons.Fill.FillStar
    val ArrowClockwise: ImageVector get() = PhosphorIcons.Regular.ArrowClockwise
    val ArrowCounterClockwise: ImageVector get() = PhosphorIcons.Regular.ArrowCounterClockwise
    val ArrowLeft: ImageVector get() = PhosphorIcons.Regular.ArrowLeft
    val ArrowsClockwise: ImageVector get() = PhosphorIcons.Regular.ArrowsClockwise
    val CaretRight: ImageVector get() = PhosphorIcons.Regular.CaretRight
    val CheckBold: ImageVector get() = PhosphorIcons.Bold.BoldCheck
    val CheckCircleFill: ImageVector get() = PhosphorIcons.Fill.FillCheckCircle
    val DownloadSimple: ImageVector get() = PhosphorIcons.Regular.DownloadSimple
    val Eye: ImageVector get() = PhosphorIcons.Regular.Eye
    val EyeSlash: ImageVector get() = PhosphorIcons.Regular.EyeSlash
    val FlowerTulip: ImageVector get() = PhosphorIcons.Regular.FlowerTulip
    val FlowerTulipFill: ImageVector get() = PhosphorIcons.Fill.FillFlowerTulip
    val Folder: ImageVector get() = PhosphorIcons.Regular.Folder
    val Gear: ImageVector get() = PhosphorIcons.Regular.Gear
    val GitMerge: ImageVector get() = PhosphorIcons.Regular.GitMerge
    val Grains: ImageVector get() = PhosphorIcons.Regular.Grains
    val Heart: ImageVector get() = PhosphorIcons.Regular.Heart
    val HeartFill: ImageVector get() = PhosphorIcons.Fill.FillHeart
    val Images: ImageVector get() = PhosphorIcons.Regular.Images
    val ImagesFill: ImageVector get() = PhosphorIcons.Fill.FillImages
    val Info: ImageVector get() = PhosphorIcons.Regular.Info
    val InfoFill: ImageVector get() = PhosphorIcons.Fill.FillInfo
    val List: ImageVector get() = PhosphorIcons.Regular.List
    val MagnifyingGlass: ImageVector get() = PhosphorIcons.Regular.MagnifyingGlass
    val PencilSimple: ImageVector get() = PhosphorIcons.Regular.PencilSimple
    val PlayFill: ImageVector get() = PhosphorIcons.Fill.FillPlay
    val SignOut: ImageVector get() = PhosphorIcons.Regular.SignOut
    val Spinner: ImageVector get() = PhosphorIcons.Regular.Spinner
    val SquaresFour: ImageVector get() = PhosphorIcons.Regular.SquaresFour
    val SquaresFourFill: ImageVector get() = PhosphorIcons.Fill.FillSquaresFour
    val Stack: ImageVector get() = PhosphorIcons.Regular.Stack
    val Tag: ImageVector get() = PhosphorIcons.Regular.Tag
    val Trash: ImageVector get() = PhosphorIcons.Regular.Trash
    val UploadSimple: ImageVector get() = PhosphorIcons.Regular.UploadSimple
    val Users: ImageVector get() = PhosphorIcons.Regular.Users
    val UsersFill: ImageVector get() = PhosphorIcons.Fill.FillUsers
    val WarningCircleFill: ImageVector get() = PhosphorIcons.Fill.FillWarningCircle
    val XBold: ImageVector get() = PhosphorIcons.Bold.BoldX
}
