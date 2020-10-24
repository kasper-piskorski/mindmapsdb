/*
 * Copyright (C) 2020 Grakn Labs
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package grakn.core.pattern.constraint.thing;

import grakn.core.common.exception.GraknException;
import grakn.core.pattern.variable.ThingVariable;
import grakn.core.pattern.variable.VariableRegistry;
import grakn.core.traversal.Traversal;
import graql.lang.common.GraqlToken;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static grakn.common.collection.Collections.set;
import static grakn.common.util.Objects.className;
import static grakn.core.common.exception.ErrorMessage.Internal.ILLEGAL_STATE;
import static grakn.core.common.exception.ErrorMessage.Pattern.INVALID_CASTING;
import static graql.lang.common.GraqlToken.Comparator.EQ;

public abstract class ValueConstraint<T> extends ThingConstraint {

    final GraqlToken.Comparator comparator;
    final T value;
    private final int hash;

    ValueConstraint(final ThingVariable owner, final GraqlToken.Comparator comparator, final T value) {
        super(owner);
        this.comparator = comparator;
        this.value = value;
        this.hash = Objects.hash(this.comparator, this.value);
    }

    public static ValueConstraint<?> of(final ThingVariable owner,
                                        final graql.lang.pattern.constraint.ThingConstraint.Value<?> valueConstraint,
                                        final VariableRegistry register) {
        if (valueConstraint.isLong()) {
            return new Long(owner, valueConstraint.comparator(), valueConstraint.asLong().value());
        } else if (valueConstraint.isDouble()) {
            return new Double(owner, valueConstraint.comparator(), valueConstraint.asDouble().value());
        } else if (valueConstraint.isBoolean()) {
            return new Boolean(owner, valueConstraint.comparator(), valueConstraint.asBoolean().value());
        } else if (valueConstraint.isString()) {
            return new String(owner, valueConstraint.comparator(), valueConstraint.asString().value());
        } else if (valueConstraint.isDateTime()) {
            return new DateTime(owner, valueConstraint.comparator(), valueConstraint.asDateTime().value());
        } else if (valueConstraint.isVariable()) {
            return new Variable(owner, valueConstraint.comparator(), register.register(valueConstraint.asVariable().value()));
        } else throw GraknException.of(ILLEGAL_STATE);
    }

    @Override
    public boolean isValue() {
        return true;
    }

    @Override
    public ValueConstraint<?> asValue() {
        return this;
    }

    @Override
    public Set<grakn.core.pattern.variable.Variable> variables() {
        return isVariable() ? set(asVariable().value()) : set();
    }

    public GraqlToken.Comparator comparator() {
        return comparator;
    }

    public T value() {
        return value;
    }

    public boolean isValueEquality() {
        return comparator.equals(EQ) && !isVariable();
    }

    public boolean isLong() {
        return false;
    }

    public boolean isDouble() {
        return false;
    }

    public boolean isBoolean() {
        return false;
    }

    public boolean isString() {
        return false;
    }

    public boolean isDateTime() {
        return false;
    }

    public boolean isVariable() {
        return false;
    }

    public Long asLong() {
        throw GraknException.of(INVALID_CASTING.message(className(this.getClass()), className(Long.class)));
    }

    public Double asDouble() {
        throw GraknException.of(INVALID_CASTING.message(className(this.getClass()), className(Double.class)));
    }

    public Boolean asBoolean() {
        throw GraknException.of(INVALID_CASTING.message(className(this.getClass()), className(Boolean.class)));
    }

    public String asString() {
        throw GraknException.of(INVALID_CASTING.message(className(this.getClass()), className(String.class)));
    }

    public DateTime asDateTime() {
        throw GraknException.of(INVALID_CASTING.message(className(this.getClass()), className(DateTime.class)));
    }

    public Variable asVariable() {
        throw GraknException.of(INVALID_CASTING.message(className(this.getClass()), className(Variable.class)));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ValueConstraint<?> that = (ValueConstraint<?>) o;
        return (this.owner.equals(that.owner) &&
                this.comparator.equals(that.comparator) &&
                this.value.equals(that.value));
    }

    @Override
    public int hashCode() {
        return hash;
    }

    public static class Long extends ValueConstraint<java.lang.Long> {

        public Long(final ThingVariable owner, final GraqlToken.Comparator comparator, final long value) {
            super(owner, comparator, value);
        }

        @Override
        public boolean isLong() {
            return true;
        }

        @Override
        public Long asLong() {
            return this;
        }

        @Override
        public Double asDouble() {
            return new Double(owner, comparator, value);
        }

        @Override
        public List<Traversal> traversals() {
            return null;
        }
    }

    public static class Double extends ValueConstraint<java.lang.Double> {

        public Double(final ThingVariable owner, final GraqlToken.Comparator comparator, final double value) {
            super(owner, comparator, value);
        }

        @Override
        public boolean isDouble() {
            return true;
        }

        @Override
        public Double asDouble() {
            return this;
        }

        @Override
        public List<Traversal> traversals() {
            return null;
        }
    }

    public static class Boolean extends ValueConstraint<java.lang.Boolean> {

        public Boolean(final ThingVariable owner, final GraqlToken.Comparator comparator, final boolean value) {
            super(owner, comparator, value);
        }

        @Override
        public boolean isBoolean() {
            return true;
        }

        @Override
        public Boolean asBoolean() {
            return this;
        }

        @Override
        public List<Traversal> traversals() {
            return null;
        }
    }

    public static class String extends ValueConstraint<java.lang.String> {

        public String(final ThingVariable owner, final GraqlToken.Comparator comparator, final java.lang.String value) {
            super(owner, comparator, value);
        }

        @Override
        public boolean isString() {
            return true;
        }

        @Override
        public String asString() {
            return this;
        }

        @Override
        public List<Traversal> traversals() {
            return null;
        }
    }

    public static class DateTime extends ValueConstraint<LocalDateTime> {

        public DateTime(final ThingVariable owner, final GraqlToken.Comparator comparator, final LocalDateTime value) {
            super(owner, comparator, value);
        }

        @Override
        public boolean isDateTime() {
            return true;
        }

        @Override
        public DateTime asDateTime() {
            return this;
        }

        @Override
        public List<Traversal> traversals() {
            return null;
        }
    }

    public static class Variable extends ValueConstraint<ThingVariable> {

        public Variable(final ThingVariable owner, final GraqlToken.Comparator comparator, final ThingVariable variable) {
            super(owner, comparator, variable);
        }

        @Override
        public boolean isVariable() {
            return true;
        }

        @Override
        public Variable asVariable() {
            return this;
        }

        @Override
        public List<Traversal> traversals() {
            return null;
        }
    }
}
