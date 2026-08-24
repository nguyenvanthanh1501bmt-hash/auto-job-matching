"use client";

import {useState} from "react";
import {
  QueryClient,
  QueryClientProvider
} from "@tanstack/react-query";

type Props = {
  children: React.ReactNode;
};

export function QueryProvider({children}: Props) {
  // Một QueryClient ổn định giữ cache xuyên suốt vòng đời provider.
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            // Cache dùng lại dữ liệu trong 30 giây; hook cụ thể có thể override.
            staleTime: 30_000,
            // Tránh refetch bất ngờ khi user quay lại tab.
            refetchOnWindowFocus: false
          }
        }
      })
  );

  return (
    <QueryClientProvider client={queryClient}>
      {children}
    </QueryClientProvider>
  );
}
