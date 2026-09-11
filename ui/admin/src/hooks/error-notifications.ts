import {useEffect, useState} from "react"


export function useErrorNotification(timeout: number = 10_000) {
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  
  useEffect(() => {
    if (errorMessage) {
      const timer = setTimeout(() => setErrorMessage(null), timeout)
      return () => clearTimeout(timer)
    }
  }, [errorMessage, timeout])
  
  return { errorMessage, setErrorMessage }
}