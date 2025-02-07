import com.dochyphen.app.api.annotation.DocumentAuditRequired
import com.dochyphen.app.api.service.sharingsession.SharingSessionDocumentAuditService
import jakarta.annotation.Priority
import jakarta.interceptor.AroundInvoke
import jakarta.interceptor.Interceptor
import jakarta.interceptor.InvocationContext
import org.slf4j.LoggerFactory

@DocumentAuditRequired
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
class DocumentAuditInterceptor {

    companion object {
        private val logger = LoggerFactory.getLogger(DocumentAuditInterceptor::class.java)
    }

    @AroundInvoke
    fun checkAuditUsage(context: InvocationContext): Any {
        val method = context.method
        val auditServiceUsed = method.declaringClass.declaredMethods.any {
            it.returnType == Void.TYPE && it.parameterTypes.contains(SharingSessionDocumentAuditService::class.java)
        }

        if (!auditServiceUsed) {
            logger.warn("Audit service not used in method: ${method.name}")
            throw IllegalStateException("Audit service must be used in method: ${method.name}")
        }

        return context.proceed()
    }
}