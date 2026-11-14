package com.xtc.statusbar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Base64;

/**
 * Connectivity paper-plane indicator (DC_SYSUI style).
 *
 * The vendor 1.8.00 resource {@code im_online}/{@code im_offline} is a blue
 * "IM" badge with the Android robot; the DC_SYSUI reference build replaced it
 * with a green / red paper-plane glyph.  Resources have to stay byte-identical
 * in this rebuild (Mode A), so the two small PNGs are embedded here and decoded
 * at runtime - same pixels, no resource change.
 */
public final class NetworkPlaneIcons {

    private static final int TARGET_W_DP = 12;
    private static final int TARGET_H_DP = 14;

    private static final String B64_ONLINE =
            "iVBORw0KGgoAAAANSUhEUgAAAMgAAADICAYAAACtWK6eAAAWZklEQVR42u2dfZQcZZXGn1vdVT0zKEaFhOkObsSYdA2J6Am7iqK4" +
            "uATWRMyEbCTx46gbFHVV3EXPyqIieOQoRtfvBTQDQhKY7kkHg3wJEiWHj4QBhExVZ4wxJNPVk4BAjCTTVd11948E+TAzzEy/Vd1V" +
            "fX9/JjPvV71P3ee+U3ULEARBEARBEARBEARBEARBEARBEARBEARBEARBEARBGAcZ98ST0l72axP9vaQsnRBnpo+Y7/U1LGL43Uhq" +
            "8yb6+yRLKMSNNM86hj2tG0SLiPHeQ//Kex2jOE0iiNDSNoq51g0Piwh0EviFoUBbNZk2RSBCvGwU0TFHtEo1v2cybYvFEmJko0bZ" +
            "5IR9Jd2eIhFEaG0bNRqMnsn2JwIRYmOjRo0g7E9aIGKxhFjYqDGoOIbdJhFEaG0bNaq7mry9EoEIsbFRowqkSnUJRCyWEAcbNSqO" +
            "Yde1xyWCCJG3UWPc/6+ptwURiBB5GzWqvSKsqlticsmEqNuooOyVRBAhBjZqVNaqaEQEIkTaRo3hjXrUNCMIEbZRQdoriSBC1G3U" +
            "aLf9gqqmRCBCdG3UaNS4R53WBCGiNipoeyURRIiujRrtjs+4VWV7IhAhmjZqdIn0KG1NtoEQRRsVhr2SCCJE0kaNBoPuVt2mCERs" +
            "VARt1Cho9T97JRZLiLyNGtVe6bYGUhv7JIKIjYoL96kWhwhEbFRsYKi3VyKQFrFRfoxs1Ggk9uxfLQIRWt1GjcZDQ8cPHRSBCC1t" +
            "o8YwWD1BtSwCERsVeQy97XoRiNDqNmo0rJ30yDMiEKHFbdQoEPUE2bwIRGxUpKkmtetEIGKjWt1GHTl4gHbspa17RCBio4QjwdwT" +
            "dBciELFR0dWHQb8IPkoJjbdRhEUAnSQrMhF1wHFSdkYiiNgo4Uj60IJ59koEIjZqslQApJplMFqNrwujH7FYYqPGSoKHmChHwMcA" +
            "TGmikT3lGPZrJYKIjWoU95PPOaSMXrjeZlBTiQNgXhVWVyIQsVHP7ztCTiPKlZJW7lD06/ojCJ3NNk4N+EVYfYnFanUbRTREzDkQ" +
            "50p68b6/id01twI4sQlH/Kxj2K+QCCI2KngbRZxjL5ErdWzd/aJI6JpbmlQcUF33SgQiNuolNorzmq/lSoaVB8H/u/m55u8AnNy0" +
            "40ft2lDlKFu+FWwUSsTIcZVzTkfx3lHF73XdBuYzm3gmNcewQ72pSwSJsY1i4AHN5xyzliu1W7vGjIyeWWhycQBAT9gdikDiaKOY" +
            "+rQEep3E1HWgjdWXna9rrgFjUQQOFK4NvUuRQ0xsFMMhDb0Mzjn66DbqCOL4OYCPR2GKquvuSgRpFRtFlKvV/L7hVHHnhCKla/4o" +
            "KuIgxvWN6LdlBRJ1G0VEfexzLm10rOunfm/C869krwDwmchMWKNrGnO+ITYqQqqAQ4wc1yjvtFubJn1zqJiXgvCVKE29EfaqpSJI" +
            "xG3UZvIpV0vV+vbQtj/V01ZnJfvlkMTxBwBvVDJ/Qq5Rax9rgUTeRjHWcQK9RyW89dtpe6Xe9jpHzAuI8M0QIl0BjG5VzSUI14hA" +
            "grBRUSx4wCiThl4f1OcY1j2qmu10s58g4HuBD59xiwbsUrnkQ0n7FhFIi9soAjYzUS7ha4Xd+tY/Kr1hVM0Ps48rQ5jGneWUvSDt" +
            "mpbC04gNjbwukRdI9E+jsI59zuvGSGEn7RxRHk2rJy5h3/9FCAr/3ZQkLYCXnQ+GqSwi+f41IpBWs1GEMphzPmHdsF78bWBrVDUX" +
            "sO8HnuAy8ID3jL/QOnab2+l2nanyOzblVHGdCKRlbBRvYaKcltQKQzSwPdAbiHfi6ez7N4cwrYc1nRc8eey2/QCggecrvFf9utHX" +
            "LBICiXzBA0aBQX0wOgoO9R8IPLp62VOY/btCmNmArmPB41T8MwBkDna9h8FzFGb8PSKQuNooxjA07tWAwpBR3BhWt51udh4z3RtC" +
            "V9v9Gi983CiW/zZljecrnYtxVN4RgcTNRtEWZu4j5kJJLw6Gum6V7Fwf9GAIXe0inxcMt7/k2S+CSoHcM5lHaGIrkMjbKEKBfVrn" +
            "7cP6J6bafw092nJ2lu/RoyF0tUdjXjjU9mLxH+dlTwPjzQrztVXNcFkbLpCIn0YNgylPRIWSPvCbRg3jOM7O4HDE8TSDFwylio+9" +
            "9D8SgMrkHJV93NfSAol4wYMHQdwH31/vpAaLjRzI8Tw7XfPoYQRf9fBZIl7g6MX+I+fTpOxtRCJsfu5UrKUEEoPTqPVEKFR0v/Ak" +
            "Nf4CpnnWMTVXezCEwm5Vv8YLh9ufLwv0onEc7DoV4Hnq1pl6muWShyKQiH8MZg+APHysd9rsO5tlUK/hmUeTl3yAiYMv7Ea8YLh9" +
            "jJM4xadXPFLNt4RAom6jmKkAaIVyaqvdTAObwTPaXE+/l8EnBK4Njd9XShbvGPuGj/kKX9Z41Dl68MnYCiQGp1E3EfP6lO4WdtCO" +
            "fU1o8zTXa78HIRR2Y+YlTrJ489juwHwbA29VNz30NNNyKxNIxE+j9oCxDoSCo9u/buahpj1zI8Io7Ma8vJwqvuxJEhPPB6uLHwm9" +
            "louVQCJuo/qJUKj5tH44ZQ00+2APF3Z7Z/D3C/5YKVVcO86EWmUtrcEhGixFXiDRP42iDSAUkgcS63dNeezpKAw5rMJuDP6kYxSv" +
            "Gc/PdrqzTwbwdoVRq6fZ1n3CAklXspdE8zSK9zJoHXxeX26zb4/SyMMr7MafLRvFq8b70xprZyp0V0gaem/kBeKkipd0ulmHEMob" +
            "aip4mAgFLUmF3WRvjVqwO1zYbVngtsrnC0ttxR9NLBArfPaKsWsXPbYj8gIBgLJRvCpTzTrsN/Z1yJcx0jeDsV7TtcIQDTyFCBJW" +
            "YTcmvshpK66cyO9k3BNPYvjvUjYIrTmevVKWpJeSxZs7XXMeMTaAkG6S+TxBoIJPVCjrA7chwoRW2I34krJevHziovLPUmmxfT+R" +
            "a8brULeDPP7Z2emart2Exn5T4hEwr9fAhaHUtkcRccIq7EbMl5dSxYsmd2iQvQtMp6vKDx2jOC1WEeQ5dh+1zZnH895edg/2gfh9" +
            "oabdjFtAKNBIbX0z/fW1HsIr7EYrSyn7osmNcY4Jrp2ubijaqma9HpqKRvqp33NS1tkMDjdxJyqWDftnsRGHa15ARMEXdtPoh45h" +
            "XTj5yOMvUHunQ2+sBfKC5P18EH8tPH/I/5l2zRviII60a346jMJuBLrKSVqfqzOhVnZ6RYR9jmE93BICAQBHL17K4E+EOIcPpF3z" +
            "ntfx3FdHOHKsAPDjEG4p15QM65N15Zwjc94A4AyF0aOnma+NFkSjZaN4NflYCPz9RyID4tSqV+2fVjHnRE0cGdf8CAFXB5+wYa1j" +
            "WB+rt5mqVlOdZ97YcgIBgFKb/Ssf2tvACOvZmtcnCA91jmTPjIo40hXzXAYC/6wYEfU5KXu5mvRFaWGGSsmw729JgQDAsDGwpeb7" +
            "7yTwlpDmo5NGt6Xdrqb/alJn1ewGYS2CV8eGkm4tUSLoA3OOZ4ayG1CzPdoeukAAYE/7tj+5unY6CDeFNy3+eaZiNu0HYjJVcwH5" +
            "CL6kJuOOKYNYoqy9ZPX9KvcM1/jGZhdIqF/t6XTNnxJwfmgyAV9ZNornN9OCpz3zDDDuCH7u2Fh9ht73xFRLWQmidMXcAMJCVe01" +
            "6qtRTRVBXpy8258ixlfDUz99Mu2ZG2byzFQzLPbh2lF3hDDxexMHtXNUimPqX+dMU1sYrjHfHGxqgQBAKWVfFuoxMGPhAU+/bxrP" +
            "fn1DbZWXPUVj2hhCV/1ehc4ZepXaBzR1o7YYgKHsspB/gwhk1EhSvJoZiwHUQuryLYlq4v6013VqQxLyQ/Vy7w7BVj2GGi1+4hXW" +
            "sPK21Z5eoawXbxeBjLVAKbsAoncD2B1OJOGpYL4nU+1aGuY8p1eycwl0J4Iu7MYYTPjaYqfd2qW66dc9M/fVgFKBrEVE0BrZuaNb" +
            "m8C1+QxsDs1x+XxjxjW/EIqt4uwsH3Q7EHBhN8ZOH1r3UFsw3xzxOrwlYHSo23XaDSKQ8YokNVikkdoCENaHl5bgu2kve0WgCTln" +
            "Z8DTbgUh2MJuBIeIuodTA1ZwC6Yp/eOrkxz4pQhkIgt29OCTjm53A/hJeMk7XZh2zTVBNH08z05rHt0cQmG3P2seLS4Z1iNBdTBt" +
            "+E1HESmsnEgoIEJozTQYx7A/A6ZLQ+xyWdo1fzv1r3OUvayT5lnH1DztJgRf2G0/16h7qMN6IMhOkq9xlwB4pTJ9+HSDCKQuy2V9" +
            "DeDPhtjluxKpmpL+XsMzj4aXWIfg366sEFF3uV3dd9RHt6P0ryrbKxlWXgRSdyQp/giMZQCqwW8AvrKs2xfX284MntHWVjV6AQRe" +
            "2M3XqLukW8F/g/DBebrKPw4S41ZQaE94x1cghyKJfQMRnQVgV2DiYM4reRSFobnV9rVhFHaDRouGk9atYVyD9Nxnl4Ch7D0bn6Jz" +
            "vNv0AgGAkm7dRdDOBkH5I9EMurvdcFco2UieuTqMwm6k0VInaYX30CdB6au1CX1/XgSiWiTGwO81r7ZE8dPAj+p6YoWK6u2drnkt" +
            "gHMDt4IafaiUtEIujUMKH22nu4do6KAIJACGOgZLU5K0FMDPFVwpxwedp6KKX9o1rybgIyHYqo+Xk9bqMNe8s5pdDEBhMXJegwii" +
            "RWWgFlmuY9grwPhWHc1U/AStGDaszQrE8WMAK4KeN4E+5SSt8F8s8un9KpvTDyT7RCDhJO//TUSTKlnDTCtUJLgZ1/wegE+HYHE+" +
            "XzKs/2vEOpPahxPvi0oV/cgL5HDyvpLAHwUw7g/NM3BBOWVdX3fkqJjfYuCCEHboFx3D+kEj1jdTzS4E47hWt1eRFcih5L14Lfno" +
            "BvD4y0cOfKNs2N+v25dXzMtA+FLgCTnR/zi69Z1GrS371K2yPc/V8iKQRoikzf6VBloKoH+MH/tpOWXX/X56xjMvJsLFgU+K6Otl" +
            "3fpmQxdWYWEGAA8F8X6KCGScDBnW5ppfXQqiI1Vz73UMu+5cIe2ZX2LGZcFrgy53dOuSRq5n2svOByGjbk7RtVexEAgA7Gn7ww43" +
            "WVtKwPM5BuEux7A/ULetcs0L6jw5G+9OWlnSrYsavphMS1Q25ycpH+W9lURMeJK27Qfw4YxrPsngf9aSibrfHEy75qcRQr1caPRD" +
            "Jzn5YtKKUXl6ZZXJfjzK+0pDzCgZ9hfIx9J6vyoVVr1cJcWkFTHdy74bwD8oPGxYHfX9FDuBHErei4N1JeRh1ctVUExaqR1SbK+o" +
            "VusTgcSMsOrlArxGRTFpxZylMDLucNq2bROBxIiw6uUycd4xih9sqhuDl307gDcomyPz6jjsCRHIc7YqrHq5xL9MJ49a3mzzZ8X2" +
            "SoOfF4HExVZ55hnsoy8EcdxWSxrL+6nfa7Y1ICh894PhxOFjqiIQ/K1ebi+CLuxG/JtkUl++hx59tumspTv7ZACzFDZ5fVz2R7KV" +
            "xZHxsqewT2tBARd2I2yqJpPLHWrOJ1qJtSUq6/z7pOXjskdaNoJ0utl57NOaoAu7EXgLqvTBvbR1T9MuBkHlZ9WeGjYGtohAIsz0" +
            "SnYuMa0BYUbAqe/vyU8sD6Jerrq1mP0mAF0Kc5nr4rRXWk4gGc7OqhGtBin13EfC9jmxPKh6ucrsEDSlp1dMlBeBRDUh5+wM9mgN" +
            "AXMDjRvMOwi0PNB6uco2tNJqLM86urVJBBJBDtfLXQNgXrA7jocSpC0Lsl6uKtKVWVmVNwsGXx+3fdMSp1hpnnWM72lrAJwSbLJL" +
            "e0G0bEi3NkdhXQiJJayyPT9e9qolIsjherlrGDgt4K72EWhZlCwGExYrbK7mtNl3ikAixAye0dbm6msAnBFwVweZtHNL+sBv6op0" +
            "nvnFjNf1njDWZtrIG08A8BaF8eO6OO6h+AqEoblu+xrV5TOPdOcE07KyPnBbPY1Mr5rvBePbDO7tdLPnBb08CUqqfbRd43wctxHF" +
            "VR/HHcy+W0sE/+FMYlpaStVXEnT6yIkz/YT/CBhHvaDdy0up4F7BzbjZzQz6R1XtReGb5xJBwg5STB+qVxzgeTprtTUvFMeh/IC/" +
            "nHbNtcftn3ms6nFP51kZleIAole1XQQSfPD9eDlVf73ctHfwqjE267mU0jd0eua7lHpCL6H2j4MxtVcikEn7UjrfMeqvl5v2zC/i" +
            "UIXIsTzwWwmUS7tdyt4+JIJSgZSTxXUiEOG57fW5kmFdWW8rnZXsOWB8e5xebirAq9IVs+7vNx7HM48F41SFYlsX56stApnQZqAL" +
            "HcP6Yd3icGefTBpN/L13wlcyrlnXcWrC09XaK5/7RCACmOiikm6trLedafvfMFUD/eSlSfm4xwF8KO11bXodzz1hcvNgpQKZYmh5" +
            "EUjLhw6+pKxblytZ8JRxRd0nSMzvqLrVTZmR7MKJ/NoJfMKrwHS6spsG4xaLLFcE0ur4av5elPbMS5V9kYrQyRptOJToj48RL6X2" +
            "j4NE+bhfehFISHS65gowvqK8Yca305Xs+E7UFNsrV6+JQAQlkeMMIvxvgKcHH8145saZPHPUwhPTeXo7mM5S2Otdh+shi0CEyZPh" +
            "7CwwXzHZpHwC+cBpB1z9Dxm3681H+v+a90q19grxt1cikICZwTPa2NW+A9BJ4Rwm4HgGP5yuZJf/fZBRa69qFbdPBCLUxU7aOULE" +
            "3wSwO9SOiVZ3VszLXpCnJMB0tkIhbhp+5fYnRCBC3ZQM+/6knjwJhDvD1QguznhdeQBIu6baPw7W0Ncq108EEgK76LGnHd0+A1rw" +
            "3xt5cV7C56QrpsWaWnul+V5eBCIox0na/wHQ58O1WzBJbWHqB0sd24dEIEIwIjGsHzDxWQD+EskJEPKtdL1EIA2grBdv5xreRKDI" +
            "lehMJBMiECEEkbTbj5cM658oQpXQGXhsN239owhECI2SYX+YGF+NhLsizrfa9RGBNINIUvZlxPSBZh+n72t9IhChQSKxehOMuQAG" +
            "m3SIg8Mpa0AEIjSM3Sl7q2PYs0G0oensFXNfK14TEUgT4ujW2QCvbKYx+UR5EYjQPCIxihcy+BNNMRjGrrJhPyQCEZqKslG8GlV+" +
            "BwgN/Xwbt2j0EIFEIZJ0FO8d+Ys3C4TfNWyTkC8CEZqXp167/S+Obp8G4GcNiB97S3rxPhGIEIG8xD6PiC8Mt1fKtfKai0AiRkkv" +
            "rjz8sONIOPaK8yIQIVrJu168XavWZgL0+0BjB2HfkF7cKAIRIsdQx2DJMaw3MyMwC8SMfKuvswgk6tEkZS8F0dcDEYiviUBki8Ug" +
            "edetS0jT/k1xs5VyW32flROBCM2TvCcH8uCaCWCnkvwDyMmqikDiFUlSg0XHsF8Pxh112yuN8rKiIpCYCsU+k4m+W1cbSesmWUkR" +
            "SHyTd936L4D+fVLRg8ReiUBaIZIY1iof9FYQnp7QpiCxVyKQFmHYsDbjiY7pAO4fd8KfsPpk5UQgrRNJ0v0HHMM+BeN52JFpAwg1" +
            "WTURSAtaLvs8Bi4Y62eoxZ+9EoG0evJu2N8non8B4B8xgOgdIhARSGtT0q27qnoiDeDFVUoYdzjUf0BWSATS8uylrXscw54D4Mbn" +
            "0w+xVy8lGefJMbBRRTsU4zVyDPvcjGduZcZluq6LQAThSKSr5iJZBUEQBEEQBEEQBEEQBEEQBEEQBEEQBEEQBEEQBCEI/h+PyZ4/" +
            "5G5WWAAAAABJRU5ErkJggg==";

    private static final String B64_OFFLINE =
            "iVBORw0KGgoAAAANSUhEUgAAAMgAAADICAYAAACtWK6eAAAWXklEQVR42u2de5gcVZnG36+6u3pIkNVdLhrT3ZOQTFdnDOgmuwq6" +
            "orgElkTMhGxM4uURN3hdFXfBZ3VRI+wjj2J0ve0qSAICE5ieSQ8GuQkSJQ+XQEACzPRMIJmunowEUBYhmamqrvr2jwkgMh1mpk9V" +
            "d1V/vz+np885deq89b3n9KnvAIIgCIIgCIIgCIIgCIIgCIIgCIIgCIIgCIIgCIIgCJNgYE7ixIG0/vWpfi8uXSdEWhiZ5JlgLGcX" +
            "HYk4LZrq90m6UIicKGbhaC+e6CDQcgBnjv+VnzJM5ziJIEJT2yjPpQ5mLCfgxFdGAto4nTJFIEKkbBSBj57IF3kubZpO2WKxhAjZ" +
            "qKqD/Lmsab9eIojQ1DaqGgzeNN36RCBCZGxUVTyatkDEYgmRsFGHwTJMu0UiiNDUNuowMWBTLd8WgQjRsFFVcD2vJoGIxRKiYKOq" +
            "Yph2TWNcIogQARs1MQxcWWsZIhAh9DaqGhp7G2stQyyWEHob5Ze9kggihN5GVX3yEzarKEcEIoTaRlUVCNe2vCsWS4iEjfLTXkkE" +
            "EUJto6pHDxRUlSUCEUJro6rhkRp7JRZLCLWN8tteSQQRQmujqsK4WWVxIhAhlDbqMJ5ok9riBCGENioIeyURRAinjarur+5UXaII" +
            "RGxU+GxUdUO0UXmJMkTERkXlurKmrRHAEkGEJrZRVcwVcI9qcYhAxEZFBo14ox/likCawEYx85lRv94/uM61IhChqW3UYXjw5GGM" +
            "ikCEprZR1SCFe69EIGKjIsdBsq4RgQjNbqMmjh5A39uG8H8iEKGpbVQ1PB/tlQhEbFToSWrxqwFLBCI2SpiAPXP3HtgvEURslDDx" +
            "BGST31WIQMRGhZZETPt5AIsAQr1tFDGWg8RGTZERw7TfLBFEbJQwEYyNQVQjAhEbNVksAMmG0YdHVwczzRHERlVnmInzxHQOgNc3" +
            "jDiAP+ZM+28kgoiNqs8AZNwLUD7uaV1uzN3RSOIAAArIXolAxEb9JXkA+VzZzgNAMa0/AeBNDSjgnwcmRhnWzW2jCBgGcd4D53Ol" +
            "yj0v/r0/rT9KQHsDNvmAYdpHSgQRG+W3TbkXoLztavmFI6PlP/+smNbvR2OKA8y8Kcj6RCDNZ6O6AeTbynY3Ad5fflhMJ38L8OIG" +
            "jnhXBVyfEHUbBWAfwHkmzudKlbur/VMxrd8C4PQGFoebNe1AH+oSQSJso5hwHzzKewnKt+8ZMw/3v8WMXgA3rjgAgEGbgq5TBBJB" +
            "G8VADwFdT5bsLe8FKq/9ENA7mbG88RcU3KuCr1OIio0aAbjrtWzUqyJHSr8ChI+H4QJV592VCNIENooI9wGU94h6ckNjQ1P5bn8q" +
            "8aOwiAPANfWotGkFEoHVqB5i5J8/xt6yeCecqX65mNIvBeGzoXkQQLuyTgsDYqNCZqPyLri73axsn24hxZR+EQhfDdOF18NeNVUE" +
            "CfkW8x1MlCdQj1Ea21tLQf1p/ctAIOLYDWC+orLy9er4SAsk9KtRjC0a0KUl7d75j9eemaAvnTyPwN/03ZYwCkzoUDfPoitFID7Y" +
            "qDAmPGDg9wTuIuYeo1y5S1mfpBOfYPD3AriEm5jYVOnesyXrJhFIk9soAnZ4RPmKh8LCsv2E0n5JJz7CoJ/6L26+PWc6SwfSep/C" +
            "cwi21vO+hF4gYbdRxNjiAd3JmF2YM4Qx1eUX0/pKBgJIbkC/dY+0l+5OJZe44Jwy0RGuFIE0mY0C8HuA8+zxFmO48hu/KhnMJJd6" +
            "zL5PcJlxH0atZe0m7P6MdzqxutCdK9lbRCBNYqMA3A+ifNxFYd6w/bifFRUzLad67N0YgDV8iD17qfEMnh+PiLREoWX7Vb1vWCgE" +
            "EoHVqAJp6DnKsQuzRnDQ7/r6M/GTwN4dAYjjsSTFl7bus/8wPtdpeR/De4u6jgt+c2KoBBLy1agnCdwV02KF+UNj24Kqtz+dWERM" +
            "dwdwfY+zpi1rHTr4+5f/5i1RWceBY+1ulEUgkbNRHtBDLhWMffYgpr4LZPrimK0vJOCBANRvwqOlOfOVe78IWKJu9Yrums4WmsgK" +
            "JAKrUQUmbHFH7d72p/FC0PU/MTfZ5lR4l//a4P1gWpbbZw2+UpzxUxh4q6p6NPY2NsJ9rbtAQr4a9SQzd5MWK2TNsV/XqxH9rS2t" +
            "TsXbFUBVzwJYmhu2H3nVgI5pS1jhjx/eqNPT1AIJuY16gIAeaOg1hpwi6ugEiqkZs+BVHoL/WQ8PxMhbOr9U2VllIULZ24gE7Mge" +
            "WhVrKoGEfos5oZeYC3yEU8gO1P8GDszC0UyVB+BzYjdmVMDesvnDL6cF+nMeS8ffBWCRuvp4U6Pc8kAEEu7VKN6vMbpdivUuKI3d" +
            "3ijt2j0PR7m2fh8CSexGS3PDlW3VPo2xtkSlAyDX6W4KgYTdRjGjENOo0Gba/aj/gspL7G1Fy5it303AXL/r0kDvbytbtx12QGtQ" +
            "N/8g7MqO4JnICiQCb+rdwMy9esIpHL8HzzVa49YDmuXpdwWR9ZAJK9tK1mF/je9rTbyDPbxdYaWbGqm/lQkkzDaKwPuZtC0ao9Bm" +
            "Wr9q5LauTie3BZHYTWOsbTPt11xJinmk8LcPgDwtHymBhNxG7dQIBYept71kPdbojR1P7Mb/4HvkYD6nrexsntwcTWEuLcJgdnh0" +
            "X+gFEgEbtRXMBZuc3hNKeDYMDQ4qsRuDP5krO1dOqk2pxGIAJ6sTJjY1Wr9PWSD9aX19OH/U46cAbQszenNl69YwtTyoxG5E9Dmj" +
            "ZF82hW8oFWwMWlfoBZIz7fX96cQIBfCGmponIh4iQiHuUWFe2Xo0bKGumNKvYMYa/20VnW+Y1o+mKKglDFZUP8y28tie0AtkXCTO" +
            "Zf3p5AiBtzbqwCLgRibu9Vyn0D6MP4bQBgaW2I2Br+TK1oYpRbU5iRPZ5XcrWxQgbGzEezDtSXrOtG7cnUkscpm2ApjVGOGCnybS" +
            "CqyhkB2ybkGICSqxGwHrDdO+ZMpd7dIZKttRqWNqn9fon1pv5IxZoMoNABbX8SJ+5wG95KFgDNu7EHKCSuzGwCU50/7KtNqYTtwB" +
            "0Kmq5oeG6RwXqQjyIkb54AgvwsnFp/UeAt4fcPtvIuICHKc310C/vtZkqwJK7EbMG4yyMy1xDGb0nMc4VVlbQBsb9X5oSi5wJ5yc" +
            "aZ+FgCfuTFzMlpyfZSMijmI6eR4B/id2A/8wW3bOn+73XdBSpYOQuCvSAnkpmpjWpwj4emDWiunfimn9uiiIYyCV+AwCSOzGoMuy" +
            "pvP52gpR92otAc/NLzkPNYVAACBr2hcx+BMBXsMHi+nkXaU03hBacWQS65joxwHM1a7MmdYnayljdyp5PIFOUydY3tTI90bzo9Cc" +
            "6VzuES2b6JBIn56L7xqFvrMvpb8lbOLoSyc+ykyX+95DjM1Z0z6n1nJcUjvPjGm4vukEAgALStYvwfwOAEHtrZlDhAf7U8nTQyOO" +
            "lL5aAwVxrFhPrmyvVaQ0lZlLrPlDzr1NKRAAyJad+0HaPwC4P5A5CZAg4luKqUTDn5o0kNE7NMLmAKraapj2ShUFPT7riBRI5X4w" +
            "2tTo90nzuwKjNLb3r0ftUwHcENhVEV1RzOgNe0DMYCa5lBm+p9Rk4Db3SDXiAAA34X1A5Zhhz72+6QUCAMc+jRcM014O0E8CuzLG" +
            "RcV08icNJ4508jSP+Ub/r5+3eaP22e19sFUV6TErta+He423qQTyUjQxrU+D8LXgauRPDqT1rbvn+Z7xY1L0z46f4oFvC6Cqu112" +
            "zlaZn+uROTOPI0Bh3t36Zm1vSIGMWy77YgpwGZiBZZ6t31PMtMypqzgy8ZNI04J4Yu50tcTZqjdoJir2CgC6uhtD14lAqk3eTedy" +
            "z8MKAtyARPI2YvfegfH0NMGLI51YRKzdGUBVj7hxbUX70IEn1Y8UUpp3Nyzv5Gj1qnjBsF0AvPcQgklPzKBjGdpdxbS+KlhbpS8k" +
            "0O3wObEbMwbjHq1o3zNmqi57VxpvAKuzVxTM6l24BTIeSSrboWEJgB0BVnv9QCr5xSAqemJuso003AqfE7sBGIoTOuYNW76cOZJA" +
            "YiWAGcrE7NF1IpDJimTILlLFXgpCb2DzEuLvFlP6pb5GjtaWVqfCN8P/xG4jmsYd8027z78q1L5aa5StX4hApiKSETxjlOwOEP1P" +
            "YJUSzh/I6J1+FD2eL9e7Ef4ndvuD6/GKtiHnd35V8PBxmKly9YoYBYQIrZEaY5SszzJwUWCRhLGmP538zSNzZip7WWdgFo4GVW4I" +
            "ILHb8y57He3Dzn1+VqInEysBvE5d9MZ1IpBaVjdM++tE9LngAgm/O+E5SurbPQ9HcTy5Bf6/XWkRtI52heeoV+8f+ie18067WwRS" +
            "ayeWrB95jDUMVHyPIqCfGiX7wlrL2duKFtfWu4JI7EZEHVlzzPczCB9YhAQU2iswbg5uh3eEBQIAC8r2dRq0M8AwfaymO2dan6q1" +
            "kPWAZrG+GfA/sRsYy7Ml6+Yg7sHMp/SVgLr3bIh4M0KG1siNy5pjd1CczyKGD1ui+c5E3F6noqQ1Gf1aBJDYDcAqo2wHtumTCEpf" +
            "rZ3hOd0iENUi2es8DI6thMrdwIRdGmLrVGRvH0jrVzFjdQCTpQ8bph1wahyVy7t8Z2oYoyIQP0QyPLrPPdJeBaYrFBQ3Qi6f22bW" +
            "nsWvmE5ezsBHfb9JzB83Sva1Qfb5YEZfAfDRCqNRJ0KIFpaGtvfBNsrWOgDfqqEYi4jWZYedmn+5L2aSPwZ4ne8XzvzptrIT+ItF" +
            "LuMDKsuzuDEO5YysQF7EMO3/IKLppqxZp2KCW0wnvgfmzwRgcb5glJ26vNOieGv7PSeY4ciiH3qBAEC2ZG1g8Md4Suei0XmGaV9T" +
            "uzj0bwF0nu+Bg+gCw7R+UI/+HUwnlwF4o8LZfijtVWgFAgA507mKiToAlCbxCPsvw7S+X7M4UvrFAL7kv6vCf+ZK1nfq1bcecYfa" +
            "8uLdIpA6sKBk/ZI8XgVg52GeXv9rlO2a308vZvQLQbjQd3EA38iV7W/WtWPVHtTzoC/vp4hAJmm3hp0dGrRVIEyUzb3LKFk1zxWK" +
            "af1LYFwcgO+/JGfa6+vZn/2p5BIAb1ZoFTvDPL5CLxAAaDPH9qDFXgXgmpcHG99hmPYHaxdH8jzUtnI2SZvOG7LTzLSuWKUrVRbH" +
            "GnWHeWzFERGMATwP2B8pphPPgOm9M9ip+c3BgVTiMxxAvlwC/zBbmn4yacVtUZl3t2/B3rFSmMeVhohhmM4X2aNVqRqTFgSVL1dJ" +
            "MmlV9qq15T0AMgov7tqwj6fICQQAcvuswVq+H1i+XAXJpJXiumrtVZx6wj6W4hBeKY6UvloD/M+Xy+jMlWtPJq30aUl0Bqsrbo+x" +
            "1xqQCBIhAsyX222U7Q810rX3Z+InM3C82CsRyIQElS8XwC9eOEZRpnW103Ol9gqMbhFIVMQxni/Xf79MuMWy7LWLd05li0xA8lB7" +
            "rNpIFA5TlTkIXsqX2wX4nb+Xf30EO2uN/TjQaH1QTCUWg9GmsMhrojI+mjqCHMqXuxk+J3Yj0HY9pq/NNOqOVlJrr4i5WwQSdnGM" +
            "58vthP+J3e6vVOhDc/ce2N+ofaHy+G4G/pgtO/dHZZw0pcXqn60vBKMThFZ/XRUedpnWto+MmY3aF8XZ+gkMLFAnNr46SmOl6SLI" +
            "oXy51xIp9dwT6tAlrG33KV+uwvCh1l4hOvaq6SLIeL5crxPAQp+r2qNpvNYYcvoavlNIaTaWA1mzsl0iSAgppmbMIs/rBLDI56qG" +
            "yeM1fubLVcVAq26ofFgw6JqojZumiCADs3A0a24nGCf5+zDmpwBekx2u7AhDvzAr3toO6o7a2Il8BNk9D0d58UQnmE/xuarnmGJr" +
            "wmQxiLFC3cMB7gJz7HYRSIjY24qWiq13Eug0n6sahUarjdLYr2uygZnkBQPplvcF0Te70i1zGXibwiKvjuIYiqxA1gPamKt3EtSm" +
            "z5zoyeky1hhD1i012cBM8kwwf5vhdfWnE+f63T86PLWrVxQ9e3Xo/kaT/tnx95AWyMGZq2pNCfrY7OS8mMa/AzDzZT+PS3I+voJb" +
            "TOs7APydqvIM047kWJLNirU9XmrOl/vAIiRiGnf+uTgOPbm+PJDRN+9+I45Rvmgx+4g3qxRHmA7lFIEEhaJ8ua97Wr+s2mBlxmov" +
            "qW8dTMXfrbTpmmJ7BXSLQISXBxj4U4aCfLnFTPICBj52eB3i7UyUL6YS5ygc0EoF0layt4hAhEPioM/nTOenNc+RMvrZYP72JOs8" +
            "FkQbiym95vMbd8878hgGv0uZ2IJ5yUwEEoopB9H5OdP6Yc2RI5VYTDyN994JXy2m9ZqWU13LUho9PA09IhABBHwlW7I21FrOw8cf" +
            "eeyh465nTrOIDxfTye2D6Za507wQtQKZaXeLQEQc67OmfYmKsnTHvhQ1ryDxO1142/vHs7BPmifm4q8AOlVh19zU3gdbBNLkeIp+" +
            "Lyqm9ItI0YlUBLyJwFuLmeQFk/2O4yYUJ2bg7qjfexFIQPRlEutA+Krygpm/3Z/WJ7mipjhzyQxHBCIoEEc6eZrG9N8+WsCPFTPJ" +
            "bbvnVU88UZ6NI8A4Q6Ey7xjPhywCEWqgf26yTSO+tIZJ+WQjySmure8ebE28daKPD2oJyXslAmks7mxFCyr8HTBODKjKlOfRQ/0Z" +
            "fe2rx7Nae3XQcXpEIEJNvHcIY6zxNwGUg6yXGNceOi7ukDgQA3CWOktH2//2STwtAhFqZsGQc+8RsE8kcLAvExEuLKb1bgAYTOmK" +
            "3xxET7PcPxFIAGRMPJs1ndOI+ccBV312Ma33seIfB2Ou1i0CEZSTLTv/CtAXAq42B7WbEx+Yv290WAQi+IJhWj9gpjMA/Cmkl9Dd" +
            "TPdLBFIHcmXrVi+mnQAgdCk6Y0wiECGAyfvesZJh2n+PcGVCf2R+2XpCBCIEaLnsj4DwtTC0lZrMXolAGkUkJftiAB9s9HZWmmh5" +
            "VwTSeJGkK85YCMJgQzaQMNhu2o+JQIS6Ma9sP2qU7CyArY3WNubmix4ikMaNJmcx84aGEghxtwhEaBhyZed8Bn+iQaKHuaDkPCgC" +
            "ERpLJKZzOZP3TgLX9/g2rTmjhwgkDCIpVe7WdKeNQL+tYwwRgQiNy/zH8aesaZ0C0M/qII6ncqXKPSIQIQSTd+tcJjo/yDqJkW/m" +
            "PheBhM5yWRsObXYcC2SAxGLdIhAhXCIpW7eSF5tHhId9jR7Ac/OHxraJQITQkR0e3Zct2W8F/LNA3GQ7d0UgkZyX2KsI+IY/CoEI" +
            "RIZYBKKJaa8H8M+Ki7WM4dqOlROBCI0USbpJQw7AkKIi89KrIpBoRZIhu2iY9hwGbhN7JQIRqpAz7dOJ+Ls1RaSyfYP0pAgkutGk" +
            "5Pw7mP9F7JUIRKgaBZyNrsdvB/DsFL8q9koE0hy0Dzs7XqjYs5lx72S/s8u0e6TnRCBNw+IRHMyV7ZMms9mRga2rAFd6TQTSfJbL" +
            "tM4F6LzDC4TFXolAmlok3ydo/0iAN9HnByuOCEQE0txkzbE77FhiFgF/maXktsUjOCg9JAJpehbuPbA/a9pvAXD9i38jsVevIh7p" +
            "q2PepqQcosh2kWHaq4sZ/VEwLm6B2CtBmJCBlL5cekEQBEEQBEEQBEEQBEEQBEEQBEEQBEEQBEEQBEHwg/8HgDiKFUAG0+AAAAAA" +
            "SUVORK5CYII=";

    private static Bitmap sOnline;
    private static Bitmap sOffline;

    private NetworkPlaneIcons() {
    }

    public static Drawable get(Context context, boolean online) {
        Bitmap bitmap = decode(online);
        if (bitmap == null) {
            return null;
        }
        Bitmap scaled = scale(context, bitmap);
        return new BitmapDrawable(context.getResources(), scaled);
    }

    private static Bitmap decode(boolean online) {
        try {
            if (online) {
                if (sOnline == null) {
                    sOnline = decodeBase64(B64_ONLINE);
                }
                return sOnline;
            }
            if (sOffline == null) {
                sOffline = decodeBase64(B64_OFFLINE);
            }
            return sOffline;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Bitmap decodeBase64(String value) {
        byte[] bytes = Base64.decode(value, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    private static Bitmap scale(Context context, Bitmap bitmap) {
        float density = context.getResources().getDisplayMetrics().density;
        int w = Math.max(1, Math.round(TARGET_W_DP * density));
        int h = Math.max(1, Math.round(TARGET_H_DP * density));
        try {
            return Bitmap.createScaledBitmap(bitmap, w, h, true);
        } catch (Throwable ignored) {
            return bitmap;
        }
    }
}
