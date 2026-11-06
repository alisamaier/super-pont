import { useId } from "react";

interface Props {
  label: string;
  value?: string;
  options?: readonly { id: string; name: string }[];
  emptyLabel: string;
  onChange: (id: string) => void;
}

export function ScheduleSelect({
  label,
  value,
  options,
  emptyLabel,
  onChange,
}: Props) {
  const id = useId();
  return (
    <div className="schedule-field">
      <label htmlFor={id}>{label}</label>
      <select
        id={id}
        value={value ?? ""}
        disabled={!options?.length}
        onChange={(event) => onChange(event.target.value)}
      >
        {!value && <option value="">{emptyLabel}</option>}
        {options?.map((option) => (
          <option key={option.id} value={option.id}>
            {option.name}
          </option>
        ))}
      </select>
    </div>
  );
}
