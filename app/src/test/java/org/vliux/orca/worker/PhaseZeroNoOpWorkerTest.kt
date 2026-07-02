package org.vliux.orca.worker

import android.content.Context
import android.content.ContextWrapper
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.ForegroundUpdater
import androidx.work.ListenableWorker
import androidx.work.ListenableWorker.Result.Success
import androidx.work.ProgressUpdater
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.utils.taskexecutor.SerialExecutor
import androidx.work.impl.utils.taskexecutor.TaskExecutor
import com.google.common.util.concurrent.Futures
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseZeroNoOpWorkerTest {
    @Test
    fun doWorkReturnsSuccessOutput() = runTest {
        val worker = PhaseZeroNoOpWorker(
            appContext = testContext(),
            workerParameters = workerParameters(),
        )

        val result = worker.doWork()

        assertTrue(result is Success)
        result as Success
        assertEquals(
            PhaseZeroNoOpWorker.WORK_STATUS_COMPLETE,
            result.outputData.getString(PhaseZeroNoOpWorker.KEY_WORK_STATUS),
        )
    }

    @Test
    fun buildRequestTargetsNoOpWorker() {
        val request = PhaseZeroNoOpWorker.buildRequest()

        assertEquals(PhaseZeroNoOpWorker::class.java.name, request.workSpec.workerClassName)
    }

    private fun testContext(): Context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    private fun workerParameters(): WorkerParameters {
        val executor = Executors.newSingleThreadExecutor()
        return WorkerParameters(
            UUID.randomUUID(),
            Data.EMPTY,
            emptyList(),
            WorkerParameters.RuntimeExtras(),
            0,
            0,
            executor,
            EmptyCoroutineContext,
            workerTaskExecutor(executor),
            noOpWorkerFactory(),
            noOpProgressUpdater(),
            noOpForegroundUpdater(),
        )
    }

    private fun workerTaskExecutor(executor: Executor): TaskExecutor {
        val serialExecutor = object : SerialExecutor {
            override fun execute(command: Runnable) {
                executor.execute(command)
            }

            override fun hasPendingTasks(): Boolean = false
        }
        return object : TaskExecutor {
            override fun getMainThreadExecutor(): Executor = executor

            override fun getSerialTaskExecutor(): SerialExecutor = serialExecutor
        }
    }

    private fun noOpWorkerFactory(): WorkerFactory = object : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker? = null
    }

    private fun noOpProgressUpdater(): ProgressUpdater {
        return ProgressUpdater { _, _, _ ->
            Futures.immediateVoidFuture()
        }
    }

    private fun noOpForegroundUpdater(): ForegroundUpdater {
        return ForegroundUpdater { _, _, _: ForegroundInfo ->
            Futures.immediateVoidFuture()
        }
    }
}
