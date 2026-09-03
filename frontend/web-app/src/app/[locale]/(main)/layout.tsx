import type {ReactNode} from "react";

import {UserWorkspaceShell} from "@/components/user/user-workspace-shell";

type Props = {
  children: ReactNode;
};

export default function MainLayout({
  children
}: Props) {
  return (
    <UserWorkspaceShell>
      {children}
    </UserWorkspaceShell>
  );
}