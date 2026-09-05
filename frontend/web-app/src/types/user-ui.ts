export type WorkspaceSearchInputProps = {
  value: string;

  placeholder: string;

  disabled?: boolean;

  className?: string;

  onValueChange: (
    value: string
  ) => void;
};