package ru.palestra.hike_exercise_b.domain.utils

import io.reactivex.disposables.Disposable

/** Метод для уничтожения Rx подписки, в случае, если она не была уничтожена ранее. */
fun Disposable.disposeIfNeeded() {
    if (!isDisposed) dispose()
}